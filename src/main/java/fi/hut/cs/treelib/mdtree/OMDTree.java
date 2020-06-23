package fi.hut.cs.treelib.mdtree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTree;
import fi.hut.cs.treelib.OrderedTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.VisualizableTree;
import fi.hut.cs.treelib.common.AbstractMDTree;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.hut.cs.treelib.util.MBRPredicate;
import fi.tuska.util.Callback;
import fi.tuska.util.Converter;
import fi.tuska.util.Holder;
import fi.tuska.util.Pair;

/**
 * Abstract base class for ordered multidimensional trees (multidimensional
 * trees where an ordering is imposed on the data item MBRs).
 * 
 * <p>
 * Subclasses must implement getSearchKey(MBR&lt;K&gt;) to convert the data
 * item MBRs to search keys that are used to order the data items.
 * 
 * @author thaapasa
 * 
 * @param <K> the base key type (MBR type is MBR&lt;K&gt;)
 * @param <V> the stored data value type
 * @param <L> the data item ordering type
 */
public abstract class OMDTree<K extends Key<K>, V extends PageValue<?>, L extends Key<L>> extends
    AbstractMDTree<K, V, L, OMDPage<K, V, L>> implements MDTree<K, V, OMDPage<K, V, L>>,
    OrderedTree<MBR<K>, V, OMDPage<K, V, L>>, VisualizableTree<MBR<K>, V, OMDPage<K, V, L>>,
    Component {

    private static final Logger log = Logger.getLogger(OMDTree.class);

    protected OMDPage<K, V, L> root;
    private OMDTreeOperations<K, V, L> operations;
    private final L searchKeyProto;
    private final Converter<MBR<K>, L> searchKeyCreator;
    private final PageID rootPageID;

    public OMDTree(String identifier, String name, L searchKeyProto, PageID rootPageID,
        Converter<MBR<K>, L> searchKeyCreator, DatabaseConfiguration<MBR<K>, V> dbConfig) {
        super(identifier, name, PageID.INVALID_PAGE_ID, dbConfig);

        this.searchKeyCreator = searchKeyCreator;

        PageFactory<OMDPage<K, V, L>> factory = new OMDPageFactory<K, V, L>(this,
            searchKeyCreator);
        initialize(factory);

        this.operations = new OMDTreeOperations<K, V, L>(this);
        this.searchKeyProto = searchKeyProto;
        this.rootPageID = rootPageID;
        assert rootPageID != null && rootPageID.isValid();

        loadTree();
    }

    /**
     * Returns a converter that can convert a data MBR into the search key.
     */
    public Converter<MBR<K>, L> getSearchKeyCreator() {
        return searchKeyCreator;
    }

    protected L getSearchKey(MBR<K> mbr) {
        return searchKeyCreator.convert(mbr);
    }

    public L getSearchKeyProto() {
        return searchKeyProto;
    }

    @Override
    protected void loadTree() {
        root = pageBuffer.fixPage(rootPageID, pageFactory, false, internalOwner);
        if (root != null && root.getEntryCount() == 0) {
            pageBuffer.unfix(root, internalOwner);
            root = null;
        }
    }

    /**
     * @return an exact match for the given key
     */
    private boolean getInternal(MBR<K> key, Callback<Pair<MBR<K>, V>> callback, Owner owner) {
        if (root == null)
            return true;

        // Search using exact search
        L searchKey = getSearchKey(key);
        // Traversal with latch-coupling is enough for queries
        PagePath<MBR<K>, V, OMDPage<K, V, L>> path = new PagePath<MBR<K>, V, OMDPage<K, V, L>>(
            false);
        operations.findExactPathByFirstKey(root.getPageID(), searchKey, path, null, owner);

        OMDPage<K, V, L> page = path.getCurrent();
        assert page != null;
        boolean res = operations.findExactMatches(page, key, callback);
        pageBuffer.unfix(path, owner);
        return res;
    }

    @Override
    public Pair<MBR<K>, V> floorEntry(MBR<K> key, Transaction<MBR<K>, V> tx) {
        log.debug(getName() + " action: floor entry from " + key);

        OMDPage<K, V, L> r = getRoot(tx);
        if (r == null)
            return null;
        L searchKey = getSearchKey(key);
        Pair<MBR<K>, V> entry = root.findFloorEntry(key, searchKey, true, tx);
        pageBuffer.unfix(r, tx);
        return entry;
    }

    @Override
    public Pair<MBR<K>, V> nextEntry(MBR<K> key, PagePath<MBR<K>, V, OMDPage<K, V, L>> savedPath,
        Transaction<MBR<K>, V> tx) {
        log.debug(getName() + " action: next entry from " + key);

        // TODO: Saved path not actually used

        OMDPage<K, V, L> r = getRoot(tx);
        if (r == null)
            return null;
        L searchKey = getSearchKey(key);
        Pair<MBR<K>, V> entry = r.findNextEntry(key, searchKey, tx);
        pageBuffer.unfix(r, tx);
        return entry;
    }

    @Override
    public boolean getExact(MBR<K> key, Callback<Pair<MBR<K>, V>> callback, Owner owner) {
        if (log.isDebugEnabled()) {
            log.debug(getName() + " operation: get exact");
        }
        return getInternal(key, callback, owner);
    }

    @Override
    public boolean getOverlapping(final MBR<K> key, final Callback<Pair<MBR<K>, V>> callback,
        Owner owner) {
        return getOverlappingInternal(key, callback, true, owner);
    }

    protected boolean getOverlappingInternal(final MBR<K> key,
        final Callback<Pair<MBR<K>, V>> callback, boolean logAction, Owner owner) {

        if (logAction) {
            if (log.isDebugEnabled()) {
                log.debug(getName() + " operation: get overlapping");
            }
        }

        // We can deal live without taking a fix on the root page, because
        // traverseMDPages below will take fixes on each visited page
        // (statistics are correct)
        if (root == null)
            return true;

        MBRPredicate<K> pred = new MBRPredicate<K>();
        pred.setSearchMBR(key);
        pred.setOnlyHeight(1);
        return root.traverseMDPages(pred, new Callback<Page<MBR<K>, V>>() {
            @Override
            @SuppressWarnings("unchecked")
            public boolean callback(Page<MBR<K>, V> page) {
                OMDPage<K, V, L> jPage = (OMDPage<K, V, L>) page;
                return jPage.processMBREntries(new Callback<Pair<MBR<K>, PageValue<?>>>() {
                    @Override
                    public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                        V val = (V) entry.getSecond();
                        if (key.overlaps(entry.getFirst())) {
                            if (callback != null) {
                                boolean continueSearch = callback.callback(new Pair<MBR<K>, V>(
                                    entry.getFirst(), val));
                                if (!continueSearch)
                                    return false;
                            }
                        }
                        return true;
                    }
                });
            }
        }, owner);
    }

    @Override
    public V get(MBR<K> key, Transaction<MBR<K>, V> tx) {
        if (log.isDebugEnabled()) {
            log.debug(getName() + " operation: get");
        }
        final Holder<V> holder = new Holder<V>(null);
        getInternal(key, new Callback<Pair<MBR<K>, V>>() {
            @Override
            public boolean callback(Pair<MBR<K>, V> entry) {
                holder.setValue(entry.getSecond());
                // Return false to stop search
                return false;
            }
        }, tx);
        return holder.getValue();
    }

    @Override
    public boolean contains(MBR<K> key, Transaction<MBR<K>, V> tx) {
        if (log.isDebugEnabled()) {
            log.debug(getName() + " operation: contains");
        }
        final Holder<Boolean> found = new Holder<Boolean>();
        getInternal(key, new Callback<Pair<MBR<K>, V>>() {
            @Override
            public boolean callback(Pair<MBR<K>, V> entry) {
                found.setValue(true);
                // Return false to stop search
                return false;
            }
        }, tx);
        return found.isInitialized();
    }

    @Override
    public boolean delete(MBR<K> key, PagePath<MBR<K>, V, OMDPage<K, V, L>> savedPath,
        Transaction<MBR<K>, V> tx) {
        assert savedPath != null;
        if (root == null) {
            log.debug("Trying to delete, root is null");
            return false;
        }

        // Need to have entire path for deletion
        assert savedPath.isMaintainFullPath();
        savedPath = operations.validatePathToLeafPage(root.getPageID(), key, savedPath, tx);
        // Found the exact page
        V result = operations.delete(savedPath, key, tx);
        if (log.isDebugEnabled()) {
            log.debug(result != null ? "Deleted item " + result : "No item found to delete!");
        }
        return result != null;
    }

    @Override
    public void deleteRoot(OMDPage<K, V, L> delRoot, Owner owner) {
        if (delRoot != root)
            throw new IllegalArgumentException("Trying to delete invalid root page!");

        if (root != null) {
            // Root page is only cleared, not actually deleted since the same
            // Page ID is always used for the root
            pageBuffer.clear(root.getPageID(), owner);
            root = null;
            updateInfoPage(owner);
        }
    }

    @Override
    public int getHeight() {
        if (root == null)
            return 0;
        return root.getHeight();

    }

    @Override
    public int getMaxHeight() {
        return getHeight();
    }

    /**
     * @return the fixed leaf page whose key range contains the given key
     */
    @Override
    public OMDPage<K, V, L> getPage(MBR<K> key, Owner owner) {
        if (root == null)
            return null;
        // Traversal with latch-coupling is enough
        PagePath<MBR<K>, V, OMDPage<K, V, L>> path = new PagePath<MBR<K>, V, OMDPage<K, V, L>>(
            false);
        operations.findPathToLeafPage(root.getPageID(), key, path, DEFAULT_TRANSACTION_ID, owner);

        OMDPage<K, V, L> leaf = path.getCurrent();
        if (leaf != null) {
            // Ascend so that leaf page is not unfixed below
            path.ascend();
        }
        pageBuffer.unfix(path, owner);
        return leaf;
    }

    /**
     * Only used for visualization. Does not follow proper page fixing
     * policies.
     */
    @Override
    public Collection<VisualizablePage<MBR<K>, V>> getPagesAtHeight(int height) {
        Collection<VisualizablePage<MBR<K>, V>> pages = new ArrayList<VisualizablePage<MBR<K>, V>>();
        if (root != null) {
            root.collectPagesAtHeight(height, pages);
        }
        return pages;
    }

    /**
     * Returns the current root (fixed). Release after use!
     */
    @Override
    public OMDPage<K, V, L> getRoot(Owner owner) {
        if (root == null)
            return null;
        return pageBuffer.fixPage(root.getPageID(), pageFactory, false, owner);
    }

    @Override
    public PageID getRootPageID() {
        return (root != null) ? root.getPageID() : null;
    }

    @Override
    public boolean isEmpty(Transaction<MBR<K>, V> tx) {
        return root == null;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public OMDTreeOperations<K, V, L> getOperations() {
        return operations;
    }

    /**
     * Requires the old root to be fixed (as it is always), and also the new
     * page. Will unfix the old root page and leave the new root fixed to page
     * buffer. Will also fix the root again (so that this method can unfix
     * it). Therefore, proper usage is: 1. fix/create new root page r, 2. call
     * attachRoot(r), 3. release root page r.
     * 
     * @param page the new root. Must be fixed to buffer.
     */
    @Override
    protected void attachRoot(OMDPage<K, V, L> page, Transaction<MBR<K>, V> tx) {
        assert !page.isRoot() : String.format(
            "Trying to attach page %s which has already been attached", page.getName());

        assert this.root == null;
        assert page.getPageID().equals(rootPageID);

        this.root = page;
        pageBuffer.fixPage(rootPageID, pageFactory, false, tx);
        page.setRoot(true);
    }

    @Override
    protected void updateInfoPage(Owner owner) {
        // Do nothing, no info page for OMDTrees
    }

    @Override
    public MBR<K> getExtents() {
        return root != null ? root.getPageMBR() : null;
    }

    @Override
    public String toString() {
        return String.format("%s (h: %d), root: %s, extents: %s", getName(), getHeight(),
            (root != null ? root.getName() : "none"), getExtents());
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
        System.out.println("Root: " + root);
        if (root != null) {
            root.printDebugInfo();
        }
    }

    @Override
    public void checkConsistency(Object... params) {
        log.info("Running consistency check");
        if (root != null) {
            // Check page fixes
            assert pageBuffer.getTotalPageFixes() == 1 : pageBuffer.getTotalPageFixes() + " != 1";
            root.checkConsistency();
            // Re-check page fixes
            assert pageBuffer.getTotalPageFixes() == 1 : pageBuffer.getTotalPageFixes() + " != 1";
        }
        K proto = keyPrototype.getMin().get(0);
        traverseMDPages(new MBRPredicate<K>(), OMDTree.getEntryOrderChecker(proto,
            valuePrototype, searchKeyProto), internalOwner);
    }

    /**
     * Closes the tree by unfixing the root and setting it to null. Tree
     * cannot be used after this method!
     */
    @Override
    public void close() {
        if (root != null) {
            pageBuffer.unfix(root, internalOwner);
            root = null;
        }
    }

    @Override
    protected void checkPageLimits(OMDPage<K, V, L> prevPage, OMDPage<K, V, L> newPage,
        MBR<K> key, boolean lastPageAtThisLevel) {
        assert prevPage != null;

        if (key != null && newPage != null) {
            L nextSKey = getSearchKey(key);
            for (Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> it = prevPage
                .descendingContentIterator(); it.hasNext();) {
                Pair<L, Pair<MBR<K>, PageValue<?>>> entry = it.next();
                if (!entry.getFirst().equals(nextSKey)) {
                    // Non-identical entry, break out of the loop
                    break;
                }
                log.info("Next key is " + nextSKey + ", moving " + entry + " to " + newPage);

                // Move entry to the new page
                newPage.putContents(entry.getSecond().getFirst(), entry.getSecond().getSecond());
                it.remove();
            }
        }

        if (lastPageAtThisLevel) {
            prevPage.setSearchKey(getSearchKeyProto().getMaxKey());
        } else {
            prevPage.setSearchKeyFromContents();
        }
        super.checkPageLimits(prevPage, newPage, key, lastPageAtThisLevel);
    }

    public static <K extends Key<K>, V extends PageValue<?>, L extends Key<L>> Callback<Page<MBR<K>, V>> getEntryOrderChecker(
        K p1, V p2, L p3) {
        return new Callback<Page<MBR<K>, V>>() {
            final Holder<L> lastCoord = new Holder<L>();

            @Override
            @SuppressWarnings("unchecked")
            public boolean callback(Page<MBR<K>, V> page) {
                if (page.getHeight() > 1)
                    return true;

                OMDPage<K, V, L> oPage = (OMDPage<K, V, L>) page;
                L firstKey = oPage.getFirstEntry().getFirst();

                if (lastCoord.isInitialized()) {
                    assert !lastCoord.getValue().equals(firstKey) : firstKey + "@" + page
                        + " same as " + lastCoord;
                }

                L lastKey = oPage.getLastEntry().getFirst();
                lastCoord.setValue(lastKey);
                return true;
            }
        };
    }

    @Override
    public OMDPage<K, V, L> createIndexRoot(int height, Transaction<MBR<K>, V> tx) {
        throw new UnsupportedOperationException("Not like this in OMDTrees");
    }

    @Override
    public OMDPage<K, V, L> createLeafRoot(Transaction<MBR<K>, V> tx) {
        assert root == null;
        OMDPage<K, V, L> newRoot = pageBuffer.fixPage(rootPageID, pageFactory, true, tx);
        assert newRoot != null;
        newRoot.format(1);
        attachRoot(newRoot, tx);
        return newRoot;
    }

    @Override
    public OMDPage<K, V, L> bulkLoad(Iterable<Pair<MBR<K>, V>> keys,
        Comparator<Pair<MBR<K>, V>> entryComparator) {
        OMDPage<K, V, L> orgRoot = super.bulkLoad(keys, entryComparator);
        if (orgRoot.getPageID().equals(rootPageID)) {
            return orgRoot;
        }
        // Switch to the correct root page
        OMDPage<K, V, L> realRoot = pageBuffer.fixPage(rootPageID, pageFactory, true,
            internalOwner);
        assert realRoot != null;
        realRoot.format(orgRoot.getHeight());

        assert realRoot.getSearchKey().equals(orgRoot.getSearchKey());

        // Move all entries from the original root to the new root
        orgRoot.moveAllEntries(realRoot, null);
        // And delete the originally created root page
        pageBuffer.delete(orgRoot, internalOwner);

        realRoot.recalculateMBR();
        return realRoot;
    }
}
