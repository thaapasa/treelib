package fi.hut.cs.treelib.btree;

import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.AbstractBTreeOperations;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.internal.TreeOperations;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class BTreeOperations<K extends Key<K>, V extends PageValue<?>> extends
    AbstractBTreeOperations<K, V, BTreePage<K, V>> implements
    TreeOperations<K, V, BTreePage<K, V>> {

    private static final Logger log = Logger.getLogger(BTreeOperations.class);

    private BTree<K, V> tree;

    protected BTreeOperations(BTree<K, V> tree) {
        super(tree);
        this.tree = tree;
    }

    @Override
    protected BTree<K, V> getTree() {
        return tree;
    }

    /**
     * Deletes a key from this leaf page.
     * 
     * @param page the leaf page
     * @param key the key to delete
     * @return the associated value
     */
    public V delete(PagePath<K, V, BTreePage<K, V>> path, K key, Transaction<K, V> tx) {
        BTreePage<K, V> page = path.getCurrent();
        assert page.isLeafPage();
        if (!page.contains(key)) {
            log.warn(String.format("No entry found in %s when trying to delete %s", page, key));
            return null;
        }
        KeyRange<K> range = KeyRangeImpl.getKeyRange(key);

        if (smoPolicy.isAboutToUnderflow(page, path)) {
            if (!page.isRoot(path)) {
                // Merge page to prevent underflowing
                merge(path, tx, key);
                page = path.getCurrent();
                assert page.isLeafPage();
            }
        }
        if (!page.isRoot(path)) {
            // Root page is handled separately later on in the checkUnderflow
            // method
            assert !smoPolicy.isAboutToUnderflow(page, path);
        }

        @SuppressWarnings("unchecked")
        V value = (V) page.removeContents(range, null);
        checkUnderflow(path, tx);
        return value;
    }

    /**
     * Splits the page. Called from split() when it is ensured that the parent
     * has enough space to hold the child pointer for the new page.
     * 
     * @param pageID ignored for B-trees
     */
    @Override
    protected void splitSpaceEnsured(BTreePage<K, V> page, K key, PageID pageID,
        PagePath<K, V, BTreePage<K, V>> path, Transaction<K, V> tx) {
        assert page == path.getCurrent();

        tree.getStatisticsLogger().log(Operation.OP_PAGE_SPLIT);
        tree.getStatisticsLogger().log(GlobalOperation.GO_PAGE_SPLIT);

        // Create a sibling page
        BTreePage<K, V> sibling = tree.createSiblingPage(page, tx);

        // Sets the siblings dirty
        K newMin = moveHalfEntries(page, sibling);
        KeyRange<K> myNewRange = new KeyRangeImpl<K>(page.getKeyRange().getMin(), newMin);
        KeyRange<K> siblingRange = new KeyRangeImpl<K>(newMin, page.getKeyRange().getMax());

        // Update pointer to next page
        sibling.setNextPage(page.getNextPage());
        page.setNextPage(sibling.getPageID());

        BTreePage<K, V> parent = path.getParent();
        // Sets the parent dirty
        parent.updateKeyRange(page, myNewRange);

        // Attach sibling to parent + update sibling key range
        parent.putContents(siblingRange, sibling.getPageID());
        sibling.setKeyRange(siblingRange);

        path.ascend();
        if (myNewRange.contains(key)) {
            buffer.unfix(sibling, tx);
            path.descend(page);
        } else {
            assert siblingRange.contains(key) : String.format(
                "Neither range contains key %d (ranges %s, %s)", key, myNewRange, siblingRange);

            buffer.unfix(page, tx);
            path.descend(sibling);
        }
    }

    private void checkEntryCountAfterSMO(BTreePage<K, V> page) {
        assert page.getEntryCount() >= smoPolicy.getMinEntriesAfterSMO(page);
        assert page.getEntryCount() <= smoPolicy.getMaxEntriesAfterSMO(page);
    }

    /**
     * Merges an underflown page with a sibling page. At entry: page is fixed
     * to buffer. Before moving to moveAllAndDetach, page + sibling is fixed.
     * After moveAllAndDetach, the resulting page in path is fixed.
     * 
     * @param page the page to merge with a sibling
     */
    @Override
    public void merge(PagePath<K, V, BTreePage<K, V>> path, Transaction<K, V> tx, K key) {
        BTreePage<K, V> page = path.getCurrent();
        BTreePage<K, V> parent = path.getParent();
        assert parent != null : "Trying to merge() a root page " + page.getName();

        if (log.isDebugEnabled())
            log.debug(String.format("Merging page %s with some sibling", page.getName()));

        BTreePage<K, V> rightSibling = parent.getHigherSibling(page.getKeyRange(), tx);
        assert page != rightSibling : "Found same right sibling for key " + page.getKeyRange()
            + " in " + parent.getContents();
        if (rightSibling != null
            && rightSibling.getEntryCount() + page.getEntryCount() > smoPolicy
                .getMaxEntriesAfterSMO(page)) {
            assert rightSibling.getEntryCount() + page.getEntryCount() >= 2 * smoPolicy
                .getMinEntriesAfterSMO(page) : rightSibling.getEntryCount() + " + "
                + page.getEntryCount() + " < 2 * " + smoPolicy.getMinEntriesAfterSMO(page)
                + ", but > " + smoPolicy.getMaxEntriesAfterSMO(page);
            // More than minimum amount of entries, so we can redistribute
            // values directly between the pages
            // Sets the involved pages dirty
            redistributeEntries(page, rightSibling, parent);
            checkEntryCountAfterSMO(page);
            checkEntryCountAfterSMO(rightSibling);

            if (page.getKeyRange().contains(key)) {
                // Page is the correct page
                buffer.unfix(rightSibling, tx);
            } else {
                // The right sibling is the correct page
                assert rightSibling.getKeyRange().contains(key);
                assert path.getCurrent().equals(page);
                path.ascend();
                path.descend(rightSibling);
                buffer.unfix(page, tx);
            }
            return;
        }
        BTreePage<K, V> leftSibling = parent.getLowerSibling(page.getKeyRange(), tx);
        assert page != leftSibling : "Found same left sibling for key " + page.getKeyRange()
            + " in " + parent.getContents();
        if (leftSibling != null
            && leftSibling.getEntryCount() + page.getEntryCount() > smoPolicy
                .getMaxEntriesAfterSMO(page)) {
            if (rightSibling != null) {
                // Release right sibling (not used)
                buffer.unfix(rightSibling, tx);
            }
            assert leftSibling.getEntryCount() + page.getEntryCount() >= 2 * smoPolicy
                .getMinEntriesAfterSMO(page);
            // More than the minimum, redistribute values between these pages
            // Sets the involved pages dirty
            redistributeEntries(leftSibling, page, parent);
            checkEntryCountAfterSMO(page);
            checkEntryCountAfterSMO(leftSibling);

            if (page.getKeyRange().contains(key)) {
                // Page is the correct page
                buffer.unfix(leftSibling, tx);
            } else {
                // The left sibling is the correct page
                assert leftSibling.getKeyRange().contains(key);
                assert path.getCurrent().equals(page);
                path.ascend();
                path.descend(leftSibling);
                buffer.unfix(page, tx);
            }
            return;
        }

        BTreePage<K, V> sibling = leftSibling;
        if (sibling == null) {
            // Choose right sibling
            sibling = rightSibling;
        } else if (rightSibling != null) {
            // Left sibling chosen, unfix the right sibling
            buffer.unfix(rightSibling, tx);
        }
        assert sibling != null : "No sibling found for " + page + " in " + parent.getContents();
        int newEntryCount = page.getEntryCount() + sibling.getEntryCount();
        assert newEntryCount >= smoPolicy.getMinEntriesAfterSMO(page) : "Page has "
            + newEntryCount + " entries < " + smoPolicy.getMinEntriesAfterSMO(page);
        assert newEntryCount <= smoPolicy.getMaxEntriesAfterSMO(page) : "Page has "
            + newEntryCount + " entries > " + smoPolicy.getMaxEntriesAfterSMO(page);

        moveAllAndDetach(path, sibling, tx);
        assert page.getKeyRange().contains(key);
    }

    /**
     * Redistributes the entries between this page and its sibling.
     * 
     * Entry: page and sibling are fixed. Leaves both pages fixed!
     * 
     * Sets both siblings and the parent dirty.
     * 
     * @param page the page with lower key range
     * @param sibling the sibling with higher key range
     */
    protected void redistributeEntries(BTreePage<K, V> page, BTreePage<K, V> sibling,
        BTreePage<K, V> parent) {
        if (log.isDebugEnabled())
            log.debug(String.format("Redistributing entries between pages %s < %s", page
                .getName(), sibling.getName()));
        page.setDirty(true);
        sibling.setDirty(true);

        tree.getStatisticsLogger().log(Operation.OP_PAGE_REDISTRIBUTE);
        tree.getStatisticsLogger().log(GlobalOperation.GO_PAGE_REDISTRIBUTE);

        K separator = null;
        if (page.getEntryCount() < sibling.getEntryCount()) {
            // Case 1. this page has too few entries
            // Move entries from sibling to this page
            while (Math.abs(page.getEntryCount() - sibling.getEntryCount()) >= 2) {
                // Take first entry from sibling, and add it here
                separator = sibling.moveFirst(page);
            }
        } else {
            // Case 2. the sibling has too few entries
            // Move entries from this page to the sibling page
            while (Math.abs(page.getEntryCount() - sibling.getEntryCount()) >= 2) {
                // Take last entry from here, and move it to the sibling
                separator = page.moveLast(sibling);
            }
        }
        // Update key ranges in the pages and the parent
        assert separator != null : String.format(
            "No entries moved when redistributing pages %s and %s", page.getName(), sibling
                .getName());

        // Sibling must have higher keys
        // This pages new range is [this.min, separator)
        // Sibling's range is [separator, sibling.max)
        KeyRange<K> myNewRange = new KeyRangeImpl<K>(page.getKeyRange().getMin(), separator);
        KeyRange<K> siblingRange = new KeyRangeImpl<K>(separator, sibling.getKeyRange().getMax());
        parent.updateKeyRange(page, myNewRange);
        parent.updateKeyRange(sibling, siblingRange);
    }

    /**
     * Moves all entries from the pages into the leftmost one of them, and
     * detaches the rightmost one from the tree.
     * 
     * Entry: page(path.getCurrent) + sibling are fixed. At exit: only the
     * page(path.getCurrent) is fixed.
     * 
     * Sets both siblings dirty
     * 
     * @param page a page
     * @param sibling the page's sibling (either left or right)
     * @eturn the page that is left alive
     */
    private BTreePage<K, V> moveAllAndDetach(PagePath<K, V, BTreePage<K, V>> path,
        BTreePage<K, V> sibling, Transaction<K, V> tx) {
        BTreePage<K, V> page = path.getCurrent();
        assert sibling != null;

        tree.getStatisticsLogger().log(Operation.OP_PAGE_MERGE);
        tree.getStatisticsLogger().log(GlobalOperation.GO_PAGE_MERGE);

        BTreePage<K, V> first = null;
        BTreePage<K, V> second = null;

        // Second page will be deleted

        // if (keyRange < sibling.keyRange)
        if (page.getKeyRange().compareTo(sibling.getKeyRange()) < 0) {
            first = page;
            second = sibling;
        } else {
            first = sibling;
            second = page;
        }
        // Both closest sibling pages contain the minimum amount of pages
        // Move all values from the second page into the first page
        if (log.isDebugEnabled())
            log.debug(String.format("Moving all entries from %s to %s", second.getName(), first
                .getName()));

        BTreePage<K, V> parent = path.ascend();
        // Move all entries from second to first
        // Dirties all involved pages
        second.moveAllEntries(first, parent);
        // Update the next pointer
        first.setNextPage(second.getNextPage());

        // Delete this page from the tree
        second.detach(parent);
        buffer.delete(second, tx);

        checkEntryCountAfterSMO(first);

        // Check that parent has not underflown
        checkUnderflow(path, tx);
        path.descend(first);
        return first;
    }

    /**
     * Moves half of the entries from an overflown page into a newly created
     * empty page.
     * 
     * @param fromPage the page to move entries from
     * @param toPage the page where the entries are moved to
     * @return the separator key, e.g. the first key in toPage after the move
     */
    private K moveHalfEntries(BTreePage<K, V> fromPage, BTreePage<K, V> toPage) {
        // Move half of the children from this page into the new page
        int index = 0;
        int halfEntries = fromPage.getPageEntryCapacity() / 2;
        K firstKey = null;
        fromPage.setDirty(true);
        toPage.setDirty(true);

        for (Iterator<Map.Entry<KeyRange<K>, PageValue<?>>> it = fromPage.contentIterator(); it
            .hasNext();) {

            Map.Entry<KeyRange<K>, PageValue<?>> entry = it.next();
            PageValue<?> value = entry.getValue();
            // Skip first half entries
            if (++index > halfEntries) {
                if (firstKey == null) {
                    firstKey = entry.getKey().getMin();
                }

                // Move entry from this page to the new page
                if (log.isDebugEnabled())
                    log.debug(String.format("Moving child %s from %s to %s", value, fromPage
                        .getName(), toPage.getName()));

                toPage.putContents(entry.getKey(), value);
                it.remove();
            }
        }
        return firstKey;
    }

    /**
     * Checks and fixes underflow at a root page.
     * 
     * @param root the root page
     */
    @Override
    protected BTreePage<K, V> checkRootUnderflow(PagePath<K, V, BTreePage<K, V>> path,
        Transaction<K, V> tx) {
        assert path.size() == 1;
        BTreePage<K, V> root = path.getCurrent();
        assert root.isRoot();
        if (log.isDebugEnabled())
            log.debug(String.format("Checking root page %s for underflow", root));

        if (root.isLeafPage()) {
            // Leaf root does not underflow
            return root;
        } else if (root.getEntryCount() == 1) {
            if (log.isDebugEnabled())
                log.debug("Root underflow occured");
            path.ascend();
            assert path.isEmpty();
            // Can move the only child as the new root
            PageID childID = (PageID) root.getContents().firstEntry().getValue();
            BTreePage<K, V> child = buffer.fixPage(childID, factory, false, tx);
            assert child != null;
            child.detach(root);
            assert root.getEntryCount() == 0 : String
                .format("Children not empty after only child moved to new tree root");
            tree.attachRoot(child, tx);
            // Unfix new root after usage (attachRoot acquired all the fixes
            // it needs)
            buffer.unfix(child, tx);
            // Unfix a second time because root is now removed from the path
            // Delete the root node
            buffer.delete(root, tx);
            return child;
        }
        if (log.isDebugEnabled())
            log.debug("Path is " + path);
        return root;
    }

    /**
     * Traverses entries starting from the start entry, calling the callback
     * function for each entry.
     */
    protected boolean traverseLeafEntries(PagePath<K, V, BTreePage<K, V>> path, K start,
        Callback<Pair<K, V>> callback, Owner owner) {
        assert !path.isEmpty();

        BTreePage<K, V> leaf = path.getCurrent();
        assert leaf.isLeafPage();
        KeyRange<K> range = new KeyRangeImpl<K>(start, start.getMaxKey());

        while (leaf != null) {
            if (!leaf.getAll(range, callback)) {
                return false;
            }

            if (path.isMaintainFullPath()) {
                // Ascend the path until next page is found
                K nextKeyToFind = leaf.getKeyRange().getMax();
                if (nextKeyToFind.equals(nextKeyToFind.getMaxKey())) {
                    // We are at last page at leaf level
                    return true;
                }
                BTreePage<K, V> cur = leaf;
                while (!cur.contains(nextKeyToFind)) {
                    path.ascend();
                    buffer.unfix(cur, owner);
                    cur = path.getCurrent();
                    assert cur != null;
                }
                while (!cur.isLeafPage()) {
                    path.descend(nextKeyToFind, owner);
                    cur = path.getCurrent();
                }
                leaf = path.getCurrent();
                assert leaf != null;
                assert leaf.isLeafPage();
            } else {
                // Fetch next, linked node and fix it (latch-coupling, or
                // fix-coupling right now...)
                BTreePage<K, V> next = leaf.getNextPage().isValid() ? buffer.fixPage(leaf
                    .getNextPage(), factory, false, owner) : null;
                // Update the node on path to the new node
                path.ascend();
                assert path.isEmpty();
                path.attachRoot(next);
                // Release previous node
                buffer.unfix(leaf, owner);
                leaf = next;
            }
        }
        // All traversed
        return true;
    }
}
