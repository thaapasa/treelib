package fi.hut.cs.treelib.common;

import java.util.Deque;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDPage;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.util.MBRPredicate;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Holder;
import fi.tuska.util.Pair;

/**
 * Abstract base class for B-trees and multiversion B-Trees (MVBT). Contains
 * the most basic functionality (height, page id, type (leaf/index page),
 * etc.).
 * 
 * @author thaapasa
 * 
 * @param <K> database key type (e.g., IntegerKey)
 * @param <V> database value type (e.g., StringValue)
 */
public abstract class AbstractMDPage<K extends Key<K>, V extends PageValue<?>, L extends Key<L>, P extends AbstractMDPage<K, V, L, P>>
    extends AbstractTreePage<MBR<K>, V, P> implements MDPage<K, V, L> {

    private static final Logger log = Logger.getLogger(AbstractMDPage.class);

    /** Setting bit for storage: Is the page a root page? */
    protected static final int BIT_ROOTPAGE = 1 << 16; // Bit 16

    private SMOPolicy smoPolicy;

    // When loading a page, the MBR is reconstructed when the contents are
    // loaded (recalculated from the contents)
    private MBR<K> pageMBR;

    public AbstractMDPage(PageID id, AbstractMDTree<K, V, L, P> tree) {
        super(id, tree, tree.getDBConfig().getPageSize());
        this.smoPolicy = tree.getDBConfig().getSMOPolicy();
    }

    @Override
    public void format(int height) {
        super.format(height);
    }

    /**
     * Multidimensional pages do not have a key range really.
     */
    @Override
    protected boolean isKeyRangeUsed() {
        return false;
    }

    @Override
    public int getMinEntries() {
        // Rebalancing can affect this
        return 2;
    }

    public int getMergeAtEntries() {
        return smoPolicy.getMinEntries(this);
    }

    public int getSplitTolerance() {
        return smoPolicy.getMinEntriesAfterSMO(this);
    }

    @Override
    protected void attachChild(P page) {
        putContents(page.getPageMBR(), page.getPageID());
    }

    @Override
    protected void calculateCapacity() {
        super.calculateCapacity();
        // Must be at least 2 so that pages never become entirely empty
        // (except possibly for the root page)
        assert smoPolicy.getMinEntries(this) >= 2;
        assert smoPolicy.getMinEntriesAfterSMO(this) > smoPolicy.getMinEntries(this);
    }

    @Override
    public MBR<K> getPageMBR() {
        return pageMBR;
    }

    public void setPageMBR(MBR<K> mbr) {
        this.pageMBR = mbr;
        setDirty(true);
    }

    public void extendPageMBR(MBR<K> mbr) {
        if (pageMBR == null || !pageMBR.contains(mbr)) {
            pageMBR = pageMBR == null ? mbr : pageMBR.extend(mbr);
            setDirty(true);
        }
    }

    public void recalculateMBR() {
        // Reset page MBR; sets page dirty
        setPageMBR(countContentMBR());
    }

    protected MBR<K> countContentMBR() {
        final Holder<MBR<K>> mbr = new Holder<MBR<K>>();
        // Go through all entries
        processMBREntries(new Callback<Pair<MBR<K>, PageValue<?>>>() {
            @Override
            public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                MBR<K> curTotal = mbr.getValue();
                mbr.setValue(curTotal != null ? curTotal.extend(entry.getFirst()) : entry
                    .getFirst());
                return true;
            }
        });
        return mbr.getValue();
    }

    @Override
    public KeyRange<MBR<K>> getKeyRange() {
        return KeyRangeImpl.getKeyRange(getPageMBR());
    }

    @Override
    public abstract void putContents(MBR<K> mbr, PageValue<?> value);

    @Override
    public String getShortName() {
        if (isRoot() && Configuration.instance().isTypedPageNames())
            return "*" + super.getShortName();
        else
            return super.getShortName();
    }

    /** Bits 0-15 are reserved, 16-31 can be used by extensions */
    @Override
    protected void setCustomSettingBits(int settingBits) {
    }

    /** Bits 0-15 are reserved, 16-31 can be used by extensions */
    @Override
    protected int getCustomSettingBits() {
        return 0;
    }

    /**
     * @return true if all entries indicated continue; false if callback
     * signaled to stop at some entry
     */
    protected abstract boolean processMBREntries(Callback<Pair<MBR<K>, PageValue<?>>> callback);

    @Override
    public boolean processEntries(Callback<Pair<KeyRange<MBR<K>>, PageValue<?>>> callback) {
        throw new UnsupportedOperationException("Use processMBREntries() instead");
    }

    /**
     * Implements a breadth-first or depth-first traversal. Adds a single fix
     * for each page that is traversed.
     * 
     * INPUT: This page must have one fix <br/>
     * OUTPUT: Does not change fix count
     */
    @SuppressWarnings("all")
    public boolean traverseMDPages(final Predicate<MBR<K>> predicate,
        final Callback<Page<MBR<K>, V>> operation, Owner owner) {
        boolean depthFirst = false;
        if (predicate instanceof MBRPredicate) {
            MBRPredicate<K> pred = (MBRPredicate<K>) predicate;
            depthFirst = pred.isDepthFirst();
        }
        if (depthFirst) {
            return traverseDepthFirst(predicate, operation, owner);
        } else {
            return traverseBreadthFirst(predicate, operation, owner);
        }
    }

    public boolean traverseBreadthFirst(final Predicate<MBR<K>> predicate,
        final Callback<Page<MBR<K>, V>> operation, final Owner owner) {
        // All pages in deque have one latch added by this search
        final Deque<PageID> pages = new LinkedList<PageID>();

        final PageBuffer buffer = dbConfig.getPageBuffer();
        // Add this page to deque, latch this page to maintain the invariant
        pages.add(getPageID());
        buffer.readLatch(getPageID(), owner);

        while (!pages.isEmpty()) {
            final PageID pageID = pages.removeFirst();
            final P page = buffer.fixPage(pageID, factory, false, owner);

            final int pageHeight = page.getHeight();
            final MBR<K> pageMBR = page.getPageMBR();

            if (predicate.matches(pageMBR, pageHeight)) {
                if (!operation.callback(page)) {
                    buffer.unlatch(pageID, owner);
                    buffer.unfix(page, owner);

                    // Unlatch pages that remain queued in the list
                    buffer.disposePages(pages, true, false, owner);
                    return false;
                }
            }

            if (pageHeight > 1 && predicate.continueTraversal(pageMBR, pageHeight)) {
                final int childHeight = pageHeight - 1;

                // Loop through page's entries
                page.processMBREntries(new Callback<Pair<MBR<K>, PageValue<?>>>() {
                    @Override
                    public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                        if (predicate.continueTraversal(entry.getFirst(), childHeight)) {
                            PageID childID = (PageID) entry.getSecond();
                            buffer.readLatch(childID, owner);
                            pages.addLast(childID);
                        }
                        return true;
                    }
                });
            }

            buffer.unlatch(pageID, owner);
            buffer.unfix(page, owner);
        }
        assert pages.isEmpty();
        return true;
    }

    public boolean traverseDepthFirst(final Predicate<MBR<K>> predicate,
        final Callback<Page<MBR<K>, V>> operation, Owner owner) {
        PagePath<MBR<K>, V, P> path = new PagePath<MBR<K>, V, P>(true);
        boolean res = findPathDepthFirst(predicate, operation, path, owner);
        dbConfig.getPageBuffer().unfix(path, owner);
        return res;
    }

    /**
     * Implements a depth-first traversal, which is stopped when the
     * user-provided operation returns false to stop search. If the search was
     * stopped, this method updates the given path with a valid, latched path
     * to the leaf page. If the search was not stopped (or no matches were
     * found); the path will remain empty.
     * 
     * The user must releast the path!
     */
    public boolean findPathDepthFirst(final Predicate<MBR<K>> predicate,
        final Callback<Page<MBR<K>, V>> operation, PagePath<MBR<K>, V, P> path, final Owner owner) {
        assert path.isEmpty();
        // All pages in deque have one latch added by this search
        // The pairs are of the form <page, parent>
        // The parent page id is only a marker, it has no latches
        final Deque<Pair<PageID, PageID>> pages = new LinkedList<Pair<PageID, PageID>>();

        final PageBuffer buffer = dbConfig.getPageBuffer();
        // Add this page to deque, latch this page to maintain the invariant
        pages.add(new Pair<PageID, PageID>(getPageID(), null));
        buffer.readLatch(getPageID(), owner);

        // Pages in the queue are latched
        while (!pages.isEmpty()) {
            final Pair<PageID, PageID> pair = pages.removeFirst();
            final PageID pageID = pair.getFirst();
            final PageID parentID = pair.getSecond();
            // Add a fix to the currently processed page
            final P page = buffer.fixPage(pageID, factory, false, owner);

            log.debug("DFS descend to page " + page);
            // Check path
            if (path.isEmpty()) {
                assert parentID == null;
                // Starting the search
                path.attachRoot(page);
            } else {
                assert parentID != null;
                // Search the path upward until the correct parent is found
                while (!path.getCurrent().getPageID().equals(parentID)) {
                    P pathPage = path.getCurrent();
                    log.debug("DFS ascend from page " + pathPage);
                    // Unlatch & unfix the previous page from the path
                    buffer.unlatch(pathPage, owner);
                    buffer.unfix(pathPage, owner);
                    // Ascend path
                    path.ascend();
                }
                assert !path.isEmpty();
                // When called with the page object, does not add fixes to the
                // page
                path.descend(page);
            }

            final int pageHeight = page.getHeight();
            final MBR<K> pageMBR = page.getPageMBR();

            if (predicate.matches(pageMBR, pageHeight)) {
                if (!operation.callback(page)) {
                    // Unlatch remaining pages in the deque
                    buffer.disposePages(CollectionUtils.getPairFirstList(pages), true, false,
                        owner);
                    // Return the path, still fixed to buffer
                    log.debug("DFS successful, path " + path);
                    return false;
                }
            }

            if (pageHeight > 1 && predicate.continueTraversal(pageMBR, pageHeight)) {
                final int childHeight = pageHeight - 1;

                // Loop through page's entries
                page.processMBREntries(new Callback<Pair<MBR<K>, PageValue<?>>>() {
                    @Override
                    public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                        if (predicate.continueTraversal(entry.getFirst(), childHeight)) {
                            PageID childID = (PageID) entry.getSecond();
                            buffer.readLatch(childID, owner);
                            pages.addFirst(new Pair<PageID, PageID>(childID, pageID));
                        }
                        return true;
                    }
                });
            }
        }
        assert pages.isEmpty();

        log.debug("DFS stopped (no hit), clearing " + path);
        // path now contains the path to the last leaf page
        while (!path.isEmpty()) {
            P page = path.getCurrent();
            buffer.unlatch(page, owner);
            buffer.unfix(page, owner);
            path.ascend();
        }
        return true;
    }

}
