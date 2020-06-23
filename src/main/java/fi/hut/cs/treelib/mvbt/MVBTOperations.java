package fi.hut.cs.treelib.mvbt;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.hut.cs.treelib.stats.Statistics.Operation;

public class MVBTOperations<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMVTOperations<K, V, MVBTPage<K, V>> {

    private static final Logger log = Logger.getLogger(MVBTOperations.class);
    protected MVBTree<K, V> tree;

    public MVBTOperations(MVBTree<K, V> tree) {
        super(tree);
        this.tree = tree;
    }

    /**
     * Splits the page. For active pages, does a key split. For inactive
     * pages, does a version split.
     * 
     * @param pageID ignored
     */
    @Override
    public void split(PagePath<K, V, MVBTPage<K, V>> path, K key, PageID pageID,
        boolean checkUnderflowAfterSplit, Transaction<K, V> tx) {
        if (tx.getReadVersion() != tree.getActiveVersion())
            throw new UnsupportedOperationException(
                "Can only operate on the current tree version");

        MVBTPage<K, V> page = path.getCurrent();
        assert page != null;

        // Split child. At this point there is space in the parent to
        // accommodate the split.
        if (page.isActive()) {
            keySplit(path, key, tx);
        } else {
            versionSplit(path, key, checkUnderflowAfterSplit, tx);
        }
    }

    @Override
    public boolean insert(PagePath<K, V, MVBTPage<K, V>> path, K key, V value,
        Transaction<K, V> tx) {
        // Just check that the version is correct
        if (tx.getReadVersion() != tree.getActiveVersion())
            throw new UnsupportedOperationException(
                "Cannot add other version than the current in MVBT/TMVBT");
        return super.insert(path, key, value, tx);
    }

    public V delete(PagePath<K, V, MVBTPage<K, V>> path, K key, Transaction<K, V> tx) {
        // Just check that the version is correct
        if (tx.getReadVersion() != tree.getActiveVersion())
            throw new UnsupportedOperationException(
                "Cannot delete other version than the current in MVBT/TMVBT");

        MVBTPage<K, V> leaf = path.getCurrent();
        assert leaf.isLeafPage();

        if (!leaf.contains(key, tx)) {
            return null;
        }
        if (leaf.isFull()) {
            split(path, key, null, true, tx);
            // After split, delete from the new page
            return delete(path, key, tx);
        }
        V value = leaf.delete(key, tx);
        checkWeakVersionCondition(path, key, tx);
        return value;
    }

    /**
     * Deletes the page with the given key range (must be prefetched)
     * logically (marks the key as deleted).
     */
    @SuppressWarnings("unchecked")
    protected V deleteLogical(PagePath<K, V, MVBTPage<K, V>> path, MVKeyRange<K> range, K key,
        Transaction<K, V> tx) {
        MVBTPage<K, V> page = path.getCurrent();
        int version = tree.getActiveVersion();
        V value = (V) page.removeContents(range);
        range = range.endVersionRange(version);
        if (log.isDebugEnabled())
            log.debug(String.format("Marking child %s as deleted at version "
                + "%d in index page %s (new range %s)", value, version, page.getName(), range));
        page.putContents(range, value);
        if (!page.isLeafPage()) {
            // Update child key range
            PageID childID = (PageID) value;
            MVBTPage<K, V> child = buffer.fixPage(childID, factory, false, tx);
            child.setKeyRange(range);
            buffer.unfix(child, tx);
        }
        checkWeakVersionCondition(path, key, tx);
        return value;
    }

    /**
     * Copies the alive pages from this page into the next page. Kills the
     * pages in this page (marks end version as the current version). It is
     * assumed that the target page has enough space to hold all the pages
     * (i.e. it is either empty or the capacity has been checked).
     * 
     * @return amount of keys copied
     */
    public int copyAndKillAlivePages(MVBTPage<K, V> fromPage, MVBTPage<K, V> toPage) {
        int version = tree.getActiveVersion();
        if (log.isDebugEnabled())
            log.debug(String.format("Copying all alive pages from page %s to page %s (%d pages)",
                fromPage.getName(), toPage.getName(), fromPage.getLiveEntryCount(version)));

        assert toPage.getFreeEntries() >= fromPage.getLiveEntryCount(version) : "No space in page "
            + toPage.getName() + " to hold all alive entries of page " + fromPage.getName();

        fromPage.setDirty(true);
        toPage.setDirty(true);

        int copied = 0;
        if (fromPage.isLeafPage()) {
            fromPage.getLeafEntries().copyAliveTo(toPage.getLeafEntries(), version);
        } else {
            List<MVKeyRange<K>> toBeKilled = new LinkedList<MVKeyRange<K>>();
            for (Map.Entry<MVKeyRange<K>, PageValue<?>> entry : fromPage.getContents().entrySet()) {

                MVKeyRange<K> range = entry.getKey();
                PageValue<?> value = entry.getValue();

                if (range.containsVersion(version)) {
                    // Move entry from this page to the new page

                    // Attach with old range (start version < current ver)
                    copyToPage(toPage, range, value);
                    copied++;
                    // Mark this child to be killed in this parent page
                    toBeKilled.add(range);
                }
            }
            // Kill the marked child pointers in this parent page
            killAlivePages(fromPage, toBeKilled);
        }
        return copied;
    }

    /**
     * Overridden: copies need to have their version ranged cropped to the new
     * version
     */
    protected void copyToPage(MVBTPage<K, V> page, MVKeyRange<K> range, PageValue<?> value) {
        range = range.startVersionRange(tree.getActiveVersion());
        page.putContents(range, value);
    }

    protected void killAlivePages(MVBTPage<K, V> page, Collection<MVKeyRange<K>> toBeKilled) {
        int version = tree.getActiveVersion();
        for (MVKeyRange<K> range : toBeKilled) {
            assert range.containsVersion(tree.getActiveVersion());

            if (page.isActive(range)) {
                // Active contents can be physically moved away from this page
                page.removeContents(range);
            } else {
                PageValue<?> value = page.getContents().get(range);
                assert value != null;
                page.updateAliveIndexEntryWithKey(value, range.getMin(), range
                    .endVersionRange(version), true);
            }
        }
    }

    /**
     * Does a version split by copying the live pages to a new page. After
     * this, the new page might have to be key-split. Attaches the new pages
     * to the parent.
     * 
     * <p>
     * New page creation logic: All new pages will be created first in memory.
     * After that, they will be inserted to the parent one at a time. The
     * parent insertion will take care of the space requirements in the
     * parent.
     * 
     * @return the newly created page whose key range covers the given key
     */
    public MVBTPage<K, V> versionSplit(PagePath<K, V, MVBTPage<K, V>> path, K key,
        boolean checkUnderflowAfterSplit, Transaction<K, V> tx) {
        MVBTPage<K, V> page = path.getCurrent();
        assert page != null;
        assert !page.isActive();

        if (log.isDebugEnabled())
            log.debug(String.format(
                "Operation: Version split on page %s (%d<%d || %d>=%d)) which is%s root", page
                    .getName(), page.getLiveEntryCount(tree.getActiveVersion()), page
                    .getMinEntries(), page.getEntryCount(), page.getPageEntryCapacity(), page
                    .isRoot(path) ? "" : " not"));

        tree.getStatisticsLogger().log(GlobalOperation.GO_VERSION_SPLIT);
        tree.getStatisticsLogger().log(GlobalOperation.GO_PAGE_SPLIT);

        tree.getStatisticsLogger().log(Operation.OP_VERSION_SPLIT);
        tree.getStatisticsLogger().log(Operation.OP_PAGE_SPLIT);

        // Create a live copy of this page (unattached)
        MVBTPage<K, V> parent = path.ascend();
        MVBTPage<K, V> liveCopy = null;
        boolean isRoot = path.isEmpty();
        if (isRoot) {
            // Current (dead) page was a root page. liveCopy is therefore the
            // new root
            liveCopy = createLiveCopy(page, parent, tx);
            tree.attachRoot(liveCopy, tx);
            path.attachRoot(liveCopy);
        } else {
            assert parent != null;
            assert !parent.isLeafPage();
            if (parent.isFull()) {
                split(path, key, null, false, tx);
                parent = path.getCurrent();
                assert !parent.isLeafPage();
            }

            liveCopy = createLiveCopy(page, parent, tx);
            // Insert new sibling page to the parent router
            parent.insertRouter(liveCopy, path, tx);
            path.descend(liveCopy);
        }

        if (log.isDebugEnabled())
            log.debug("Livecopy is " + liveCopy + ", from " + page + ", " + parent);
        // Unfix the old page, which was removed from path earlier on
        buffer.unfix(page, tx);

        liveCopy = checkStrongVersionCondition(path, key, tx);
        if (log.isDebugEnabled())
            log.debug("Livecopy-after is " + liveCopy);
        assert path.getCurrent() == liveCopy;

        // Check weak version condition (for checking parent)
        if (checkUnderflowAfterSplit) {
            checkWeakVersionCondition(path, key, tx);
        }
        return path.getCurrent();
    }

    /**
     * Overridden to not copy active pages (they can be reused). Leaves the
     * old page fixed - it needs to be unfixed by the caller.
     */
    public MVBTPage<K, V> createLiveCopy(MVBTPage<K, V> page, MVBTPage<K, V> parent, Owner owner) {
        if (page.isActive()) {
            // Remove this page from parent so that it will be re-added. Adds
            // a fix to this page so that the old page unfixing will work
            // properly.
            PageValue<?> v = parent.removeContents(page.getKeyRange());
            assert v != null;
            buffer.fixPage(page.getPageID(), factory, false, owner);
            return page;
        } else {
            // Find the current pointer in parent to the page that is to be
            // copied
            MVBTPage<K, V> result = createLiveCopyFromInactivePage(page, parent, owner);
            assert result != null;
            return result;
        }
    }

    /**
     * @return a live copy of this page. This page is also marked as dead. The
     * returned page is unattached. Cannot cause splits or other structural
     * change operations. Underflow needs to be taken care of from outside
     * this page.
     */
    public MVBTPage<K, V> createLiveCopyFromInactivePage(MVBTPage<K, V> page,
        MVBTPage<K, V> parent, Owner owner) {
        assert !page.isActive();
        // Create a sibling page (unattached)
        MVBTPage<K, V> sibling = page.createSibling(owner);
        if (log.isDebugEnabled())
            log.debug(String.format("Creating a live copy of page %s, new page is %s", page
                .getName(), sibling.getName()));
        // Copy alive pages to sibling
        copyAndKillAlivePages(page, sibling);

        int endVersion = tree.getActiveVersion();
        MVKeyRange<K> myNewRange = page.getKeyRange().endVersionRange(endVersion);

        if (parent != null) {
            assert !parent.isLeafPage();
            MVKeyRange<K> oldRange = parent.findContentKey(page.getKeyRange(), tree
                .getActiveVersion());
            assert oldRange != null : page.getKeyRange() + " not in " + parent + " contents "
                + parent.getContents();

            if (parent.isActive()) {
                // Special case: parent has been created during this version:
                // this page can be deleted from parent physically
                PageValue<?> v = parent.removeContents(oldRange);
                assert v != null;
            } else {
                // Update the router to the old (dead) page (this)
                PageValue<?> v = parent.updateAliveIndexEntryWithKey(page.getPageID(), oldRange
                    .getMin(), myNewRange, true);
                assert v != null;
            }
        }
        page.setKeyRange(myNewRange);
        return sibling;
    }

    @Override
    protected void increaseTreeHeight(MVBTPage<K, V> page, K key, PageID childID,
        PagePath<K, V, MVBTPage<K, V>> path, Transaction<K, V> tx) {
        throw new UnsupportedOperationException("Tree height increase not handled here");
    }

    /**
     * This page must be attached to a parent when calling this method (or be
     * the root). This page must also be active.
     * 
     * @param key a key to look for
     * @return the page that covers the given key (this or the newly created
     * sibling page)
     */
    protected MVBTPage<K, V> keySplit(PagePath<K, V, MVBTPage<K, V>> path, K key,
        Transaction<K, V> tx) {
        MVBTPage<K, V> page = path.getCurrent();
        assert page != null;
        assert page.isActive();

        MVBTPage<K, V> parent = path.ascend();
        if (parent != null && parent.isFull()) {
            // Unfix page, which was removed from path
            buffer.unfix(page, tx);
            split(path, key, null, false, tx);
            parent = path.getCurrent();
            assert parent != null;
            path.descend(key, tx);
            return keySplit(path, key, tx);
        }

        tree.getStatisticsLogger().log(GlobalOperation.GO_PAGE_SPLIT);
        tree.getStatisticsLogger().log(GlobalOperation.GO_KEY_SPLIT);

        tree.getStatisticsLogger().log(Operation.OP_PAGE_SPLIT);
        tree.getStatisticsLogger().log(Operation.OP_KEY_SPLIT);

        MVBTPage<K, V> sibling = page.createSibling(tx);
        if (log.isDebugEnabled())
            log.debug(String.format("Operation: Key split page %s into sibling %s", page
                .getName(), sibling.getName()));
        // Page and sibling have one fix now. distributeEntries() leaves the
        // pages fixed.
        distributeEntries(path, page, sibling);

        if (parent == null) {
            assert path.isEmpty();
            // We are key-splitting a root page. The tree height needs to be
            // increased.
            MVBTPage<K, V> newRoot = tree.createIndexRoot(page, sibling, tx);
            path.attachRoot(newRoot);
        } else {
            parent.insertRouter(sibling, path, tx);
        }

        // Unfixes the other page from page buffer
        MVBTPage<K, V> resultPage = selectPage(page, sibling, key, true, tx);
        path.descend(resultPage);
        return resultPage;
    }

    /**
     * Distributes pages between this page and the given sibling. Called when
     * a key split occurs after a version split. Both pages must be fixed to
     * buffer; both are left fixed after this method.
     * 
     * @param sibling the newly created empty sibling page (not attached)
     */
    public void distributeEntries(PagePath<K, V, MVBTPage<K, V>> path, MVBTPage<K, V> page,
        MVBTPage<K, V> sibling) {
        // if (keyRange < sibling.keyRange)
        if (page.getKeyRange().compareTo(sibling.getKeyRange()) > 0) {
            // This page is after the sibling page
            distributeEntries(path, sibling, page);
            return;
        }
        if (log.isDebugEnabled())
            log.debug(String.format("Redistributing entries between pages %s < %s", page
                .getName(), sibling.getName()));

        // Normal case: this page is before the sibling page
        K separator = page.getKeyRange().getMax();

        while (Math.abs(page.getEntryCount() - sibling.getEntryCount()) >= 2) {
            // Move one entry
            if (page.getEntryCount() > sibling.getEntryCount()) {
                // Move one entry from this page into the sibling
                separator = page.moveLast(sibling);
            } else {
                // Move one entry from sibling into this page
                separator = sibling.moveFirst(page);
            }
        }

        MVBTPage<K, V> parent = path.getCurrent();
        // Update key range for the attached entries (should be only one
        // attached entry)
        MVKeyRange<K> myNewKeyRange = new MVKeyRange<K>(page.getKeyRange().getMin(), separator,
            page.getKeyRange().getVersionRange());
        if (parent != null) {
            parent.updateAliveIndexEntryWithKey(page.getPageID(), page.getKeyRange().getMin(),
                myNewKeyRange, false);
        }
        // Update this page's key range
        page.setKeyRange(myNewKeyRange);

        MVKeyRange<K> siblingKeyRange = new MVKeyRange<K>(separator, sibling.getKeyRange()
            .getMax(), sibling.getKeyRange().getVersionRange());
        if (parent != null) {
            parent.updateAliveIndexEntryWithKey(sibling.getPageID(), sibling.getKeyRange()
                .getMin(), siblingKeyRange, false);
        }
        // Update sibling's range
        sibling.setKeyRange(siblingKeyRange);
    }

    /**
     * Checks the weak version condition in the given page, fixing underflow
     * if necessary.
     */
    public MVBTPage<K, V> checkWeakVersionCondition(PagePath<K, V, MVBTPage<K, V>> path, K key,
        Transaction<K, V> tx) {
        MVBTPage<K, V> page = path.getCurrent();
        MVBTPage<K, V> parent = path.getParent();
        assert page != null;
        assert page.getKeyRange().contains(key, tree.getActiveVersion());

        boolean wasRoot = page.isRoot(path);
        if (log.isDebugEnabled())
            log.debug(String.format(
                "Checking weak version condition at page %s under parent %s with key %s", page
                    .getName(), parent != null ? parent.getName() : "-", key));
        checkWeakVersionConditionInPage(path, key, tx);

        if (log.isDebugEnabled())
            log.debug(path);
        page = path.getCurrent();
        if (page == null) {
            // Tree has been cleared
            return null;
        }
        assert page.isAlive(tree.getActiveVersion());
        assert page.getKeyRange().contains(key, tree.getActiveVersion()) : key
            + " not in range of page " + page + " at version " + tree.getActiveVersion();
        parent = path.getParent();

        // Test if we need to check weak version condition in parent
        if (!wasRoot && parent != null && parent.isAlive(tree.getActiveVersion())) {

            // Unfix old page
            buffer.unfix(page, tx);
            page = null;
            {
                MVBTPage<K, V> par = path.ascend();
                assert parent == par;
            }
            int parLevel = parent.getHeight();

            assert parent.containsPage(key, tree.getActiveVersion());
            // Check weak version condition in parent
            checkWeakVersionCondition(path, key, tx);
            parent = path.getCurrent();

            assert parent.isAlive(tree.getActiveVersion());
            assert parent.containsPage(key, tree.getActiveVersion()) : "Parent " + parent
                + " does not contain " + key + " at version " + tree.getActiveVersion();
            assert !path.isEmpty();

            // Descend to new page
            int pLevel = path.getCurrent().getHeight();
            if (pLevel == parLevel) {
                path.descend(key, tx);
            } else {
                // Root height decrease might cause us to be at a descended
                // level already
                assert pLevel == parLevel - 1;
            }
        }
        return path.getCurrent();
    }

    private boolean checkWeakVersionConditionInPage(PagePath<K, V, MVBTPage<K, V>> path, K key,
        Transaction<K, V> tx) {
        final MVBTPage<K, V> page = path.getCurrent();
        if (!page.isWeakVersionUnderflow(path))
            return false;

        int version = tree.getActiveVersion();
        if (log.isDebugEnabled())
            log.debug(String.format("Condition: Weak version underflow at page %s", page
                .getName()));
        if (page.isRoot(path)) {
            // Root underflows, so there must be either only one page left
            // or no pages (in a leaf page)
            int liveCount = page.getLiveEntryCount(version);
            if (liveCount == 0) {
                // No live pages left in tree
                // This must be a leaf page for this situation to happen
                assert page.isLeafPage() : "No children left at index page " + page.getName()
                    + " at version " + version;
                // Just clear the tree
                tree.clearInternal(tx);
                path.ascend();
                // Unfix the page, as it's removed from the path
                buffer.unfix(page, tx);
                assert path.isEmpty();
                return true;
            }
            if (!page.isLeafPage()) {
                if (liveCount == 1) {
                    path.ascend();
                    assert path.isEmpty();
                    // Make the only alive page the new root
                    // Get the first alive child id. Remove the child from
                    // the page if it was alive.
                    PageID aliveChildID = (PageID) page.getFirstAliveChild(true);
                    MVBTPage<K, V> aliveChild = buffer.fixPage(aliveChildID, factory, false, tx);
                    if (log.isDebugEnabled())
                        log.debug(String.format(
                            "Moving the only alive child %s of old root %s as the new root",
                            aliveChild.getName(), page.getName()));
                    assert aliveChild != null : "No alive children found in root page "
                        + page.getName();
                    tree.attachRoot(aliveChild, tx);
                    path.attachRoot(aliveChild);
                    // Unfix page from page buffer (removed from path)
                    buffer.unfix(page, tx);
                    return true;
                }
            }
            assert false : "Weak version underflow reported at page " + page.getName()
                + " but could not be resolved";
        } else {
            resolveWeakVersionUnderflow(path, key, tx);
        }
        return true;
    }

    /**
     * Overridden to use merges when dealing with active pages. The path will
     * contain the resulting page (fixed), the other page will be unfixed.
     */
    protected void resolveWeakVersionUnderflow(PagePath<K, V, MVBTPage<K, V>> path, K key,
        Transaction<K, V> tx) {
        MVBTPage<K, V> page = path.getCurrent();

        if (page.isActive()) {
            // Use any key as we are not interested of the outcome
            mergeWithSibling(path, key, tx);
        } else {
            // Non-active pages need to be split
            versionSplit(path, key, true, tx);
        }
    }

    /**
     * Checks strong version condition on this page. This page must be
     * attached to a parent (or be the root page). This page must also be
     * created during this version operation.
     * 
     * @param key a key to look for
     * @return the page that covers the given key (can be != this if this page
     * has been key split into two pages)
     */
    public MVBTPage<K, V> checkStrongVersionCondition(PagePath<K, V, MVBTPage<K, V>> path, K key,
        Transaction<K, V> tx) {
        MVBTPage<K, V> page = path.getCurrent();
        if (page.isStrongVersionUnderflow(path)) {
            if (log.isDebugEnabled())
                log.debug(String.format("Condition: Strong version underflow at page %s", page
                    .getName()));
            return mergeWithSibling(path, key, tx);
        } else if (page.isStrongVersionOverflow()) {
            if (log.isDebugEnabled())
                log.debug(String.format("Condition: Strong version overflow at page %s", page
                    .getName()));
            return keySplit(path, key, tx);
        } else {
            // The current entry count is okay.
            return page;
        }
    }

    /**
     * This page must be attached to a parent when calling this method (or be
     * the root).
     */
    public MVBTPage<K, V> mergeWithSibling(PagePath<K, V, MVBTPage<K, V>> path, K key,
        Transaction<K, V> tx) {
        MVBTPage<K, V> page = path.getCurrent();
        assert !page.isRoot(path) : "Merge attempted at root page " + page.getName();

        tree.getStatisticsLogger().log(Operation.OP_PAGE_MERGE);
        tree.getStatisticsLogger().log(GlobalOperation.GO_PAGE_MERGE);

        MVBTPage<K, V> parent = path.getParent();
        assert parent != null;

        // This page has fewer than strongMinEntries entries, so merge with a
        // copy of a sibling
        MVBTPage<K, V> sibling = parent.getLiveSibling(page, tx);
        if (log.isDebugEnabled())
            log.debug(String.format("Operation: Merge page %s with sibling page %s (parent %s)",
                page.getName(), sibling.getName(), parent.getName()));

        // First, create a version split (copy) of the sibling
        MVBTPage<K, V> siblingCopy = createLiveCopy(sibling, parent, tx);
        // Unfix the sibling (siblingCopy is used from now on)
        buffer.unfix(sibling, tx);

        // Now, all the pages from copy should be placed in this page, if
        // possible. If not (block overflow or strong version overflow), we
        // need to key split this page.
        if (page.getEntryCount() + siblingCopy.getEntryCount() <= page.getPageEntryCapacity()) {
            // Easy case: copy all pages here, possibly key-split this page.
            // copyAlivePages will actually copy all pages, but no matter.
            copyAndKillAlivePages(siblingCopy, page);
            MVKeyRange<K> newRange = page.getKeyRange().extendKeyRange(siblingCopy.getKeyRange());
            parent.updateAliveIndexEntryWithKey(page.getPageID(), page.getKeyRange().getMin(),
                newRange, true);
            page.setKeyRange(newRange);

            // siblingCopy will be discarded after this.
            // buffer.delete() requires exactly one fix (which we have now).
            // The fix is released.
            buffer.delete(siblingCopy.getPageID(), tx);

            // Check that the new page does not overflow.
            if (page.isStrongVersionOverflow()) {
                MVBTPage<K, V> result = checkStrongVersionCondition(path, key, tx);
                assert path.getCurrent() == result;
            }
            // result = checkWeakVersionCondition(path, key);
            // assert path.getCurrent() == result;
            return path.getCurrent();
        }
        // Other case: too many entries
        // The resulting entry count in this and siblingCopy cannot cause
        // strong version underflow or overflow, since
        // 1) the total entry count is more than pageCapacity (no underflow)
        // 2) there are less than pageCapacity + strongMinEntries pages (no
        // overflow)
        parent = path.ascend();
        distributeEntries(path, page, siblingCopy);

        // Insert the new page into parent
        parent.insertRouter(siblingCopy, path, tx);

        MVBTPage<K, V> resultPage = selectPage(page, siblingCopy, key, true, tx);
        path.descend(resultPage);
        return resultPage;
    }

    /**
     * @param key the key to look for
     * @param unfixOther if true, will unfix the non-selected page from the
     * page buffer
     * @return the page whose range contains the given key.
     */
    protected MVBTPage<K, V> selectPage(MVBTPage<K, V> page1, MVBTPage<K, V> page2, K key,
        boolean unfixOther, Owner owner) {
        if (page1.getKeyRange().contains(key)) {
            if (unfixOther)
                buffer.unfix(page2, owner);
            return page1;
        } else {
            assert page2.getKeyRange().contains(key) : String.format(
                "Neither page %s nor page %s contains key %d", page1.getName(), page2.getName(),
                key);

            if (unfixOther)
                buffer.unfix(page1, owner);
            return page2;
        }
    }

    @Override
    protected void splitSpaceEnsured(MVBTPage<K, V> page, K key, PageID pageID,
        PagePath<K, V, MVBTPage<K, V>> path, Transaction<K, V> tx) {
        throw new UnsupportedOperationException("Page splitting not handled here");
    }
}
