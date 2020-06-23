package fi.hut.cs.treelib.mdtree;

import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.AbstractBTreeOperations;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.util.MDDBUtils;
import fi.tuska.util.Array;
import fi.tuska.util.Callback;
import fi.tuska.util.Holder;
import fi.tuska.util.NotImplementedException;
import fi.tuska.util.Pair;
import fi.tuska.util.iterator.Iterables;

/**
 * Basic ordered multidimensional tree operations. J-tree and Hilbert R-tree
 * use this class directly.
 * 
 * @author thaapasa
 * 
 * @param <K>
 * @param <V>
 */
public class OMDTreeOperations<K extends Key<K>, V extends PageValue<?>, L extends Key<L>>
    extends AbstractBTreeOperations<MBR<K>, V, OMDPage<K, V, L>> {

    private static final Logger log = Logger.getLogger(OMDTreeOperations.class);

    private final OMDTree<K, V, L> tree;

    public enum SplitType {
        HALF, LINEAR_SPLIT, OPTIMAL
    };

    private SplitType splitType = SplitType.LINEAR_SPLIT;

    protected OMDTreeOperations(OMDTree<K, V, L> tree) {
        super(tree);
        this.tree = tree;
    }

    public void setSplitType(SplitType splitType) {
        this.splitType = splitType;
    }

    @Override
    protected OMDTree<K, V, L> getTree() {
        return tree;
    }

    @Override
    public void findPathToLeafPage(PageID rootPageID, MBR<K> key,
        PagePath<MBR<K>, V, OMDPage<K, V, L>> savedPath, int version, Owner owner) {
        findExactPathByFirstKey(rootPageID, tree.getSearchKey(key), savedPath, null, owner);
    }

    @Override
    public PagePath<MBR<K>, V, OMDPage<K, V, L>> findPathForInsert(PageID rootPageID, MBR<K> mbr,
        PagePath<MBR<K>, V, OMDPage<K, V, L>> path, Transaction<MBR<K>, V> tx) {
        if (path != null && !path.isEmpty()) {
            buffer.unfix(path, tx);
        }
        if (path == null) {
            path = new PagePath<MBR<K>, V, OMDPage<K, V, L>>(true);
        }
        findExactPathByFirstKey(rootPageID, tree.getSearchKey(mbr), path, mbr, tx);
        return path;
    }

    @Override
    protected boolean isAllowDuplicates() {
        // J-tree supports duplicate entries
        return true;
    }

    /**
     * @param enlargeMBR for inserts: the MBR of the inserted entry, used to
     * enlarge the page MBRs along the path. For other operatios: null.
     */
    public void findExactPathByFirstKey(PageID rootPageID, L key,
        PagePath<MBR<K>, V, OMDPage<K, V, L>> path, MBR<K> enlargeMBR, Owner owner) {
        assert path.isEmpty();

        OMDPage<K, V, L> page = buffer.fixPage(rootPageID, factory, false, owner);
        path.attachRoot(page);

        while (true) {
            if (enlargeMBR != null) {
                // extendPageMBR checks if the page MBR already covers the
                // given MBR
                page.extendPageMBR(enlargeMBR);
            }
            // Stop search after the leaf node has been reached and the leaf
            // page MBR has been extended
            if (page.isLeafPage())
                return;

            Pair<L, Pair<MBR<K>, PageValue<?>>> entry = page.getFirstCeilingEntry(key);
            assert entry != null : "No ceiling entry found for " + key + " at " + page;
            PageID childID = (PageID) entry.getSecond().getSecond();
            assert childID != null : String.format(
                "Index node %s does not contain child node with key %s", page.getName(), key);

            if (enlargeMBR != null) {
                page.enlargeRouterMBR(entry.getFirst(), childID, enlargeMBR);
            }
            OMDPage<K, V, L> child = buffer.fixPage(childID, factory, false, owner);
            path.descend(child);
            page = child;
        }
    }

    /**
     * Searches through this leaf page (and possibly overflow pages) for
     * matches of the given MBR. Fixes pages and releases all fixes this
     * method has itself acquired, so maintains the original fix state in all
     * situations.
     */
    @SuppressWarnings("unchecked")
    protected boolean findExactMatches(OMDPage<K, V, L> page, MBR<K> mbr,
        Callback<Pair<MBR<K>, V>> callback) {
        final L searchKey = tree.getSearchKey(mbr);
        assert page.isLeafPage();

        // if (page.getFirstLeafEntry() > mbr ||
        // page.getLastLeafEntry() < mbr)
        // if (page.getFirstLeafEntry().getFirst().compareTo(mbr) > 0
        // || page.getLastLeafEntry().getFirst().compareTo(mbr) < 0) {
        // // Not on this page
        // return;
        // }

        // Only traverse the pages in case the last entry is known to
        // be larger (than equal) to the searched entry
        for (Pair<MBR<K>, PageValue<?>> val : page.getEntries(searchKey)) {
            if (val.getFirst().equals(mbr)) {
                if (callback != null) {
                    boolean continueSearch = callback.callback(new Pair<MBR<K>, V>(
                        val.getFirst(), (V) val.getSecond()));
                    if (!continueSearch) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Overridden: MBRs used instead of key ranges
     */
    @Override
    protected void increaseTreeHeight(OMDPage<K, V, L> root, MBR<K> key, PageID childID,
        PagePath<MBR<K>, V, OMDPage<K, V, L>> path, Transaction<MBR<K>, V> tx) {
        assert root == path.getCurrent();
        assert root.isRoot() : String.format("Page %s is not root page, cannot increase height",
            root.getName());
        path.ascend();
        assert path.isEmpty();

        root.setRoot(false);

        if (log.isDebugEnabled())
            log.debug(String.format("Increasing tree height from page %s", root.getName()));

        // Create new root page
        OMDPage<K, V, L> onlyChild = tree.createSiblingPage(root, tx);
        assert !onlyChild.getPageID().equals(root.getPageID());
        assert onlyChild.getSearchKey().equals(root.getSearchKey());
        assert onlyChild.getEntryCount() == 0 : onlyChild.getEntryCount();
        // Move all entries from the root to onlyChild
        root.moveAllEntries(onlyChild, null);
        assert onlyChild.getSearchKey().equals(root.getSearchKey()) : onlyChild.getSearchKey()
            + " != " + root.getSearchKey();
        assert root.getEntryCount() == 0;

        // Make root a new root of a higher level
        root.format(root.getHeight() + 1);
        root.setRoot(true);

        // Add the onlyChild under the root page
        root.putContents(onlyChild);
        assert root.getEntryCount() == 1 : root.getEntryCount();

        // Descend to the new page
        path.attachRoot(root);
        path.descend(onlyChild);

        // Split this page (this page is no longer a root page)
        split(path, key, childID, true, tx);
    }

    @Override
    protected void splitSpaceEnsured(OMDPage<K, V, L> page, MBR<K> key, PageID pageID,
        PagePath<MBR<K>, V, OMDPage<K, V, L>> path, Transaction<MBR<K>, V> tx) {
        assert page == path.getCurrent();
        StatisticsLogger stats = tree.getStatisticsLogger();
        // Create a sibling page
        // page will be the left page (contains lower keys); and sibling will
        // be the right page (contains higher keys).
        OMDPage<K, V, L> sibling = tree.createSiblingPage(page, tx);

        // This is the old page's search key. It will be used as the
        // sibling page's search key.
        L pSearchKey = page.getSearchKey();

        // Sets the siblings dirty.
        // This just moves the entries, and updates pages' own MBRs.
        // The ordering by coordinates is held.
        final int splitPos = findSplitPosition(page);

        L newMax = moveSomePages(page, sibling, splitPos);

        // At this point, the pages are split and their MBRs are adjusted.
        // However, the new key to add is still missing from the MBRs.
        if (tree.getSearchKey(key).compareTo(newMax) <= 0) {
            // The new entry is going to page
            page.extendPageMBR(key);
        } else {
            // The new entry is going to sibling
            sibling.extendPageMBR(key);
        }

        OMDPage<K, V, L> parent = path.getParent();

        // Update the MBR in the router to page, and also the search key to
        // newMax. Also updates the page's MBR in the parent router.
        parent.updateSearchKey(page, newMax);

        // Attach sibling to parent. This will go to the right of original
        // page. This is maintained by putContents(), which will always add
        // new entries to the end of the list.
        sibling.setSearchKey(pSearchKey);
        parent.putContents(pSearchKey, sibling.getPageMBR(), sibling.getPageID());

        MDDBUtils.logSplit(stats, page.getPageMBR(), sibling.getPageMBR(), splitPos
            / (double) page.getPageEntryCapacity());

        path.ascend();
        // If pageID is set, must use that to determine page
        if (pageID != null ? page.containsChild(pageID) : tree.getSearchKey(key)
            .compareTo(newMax) <= 0) {
            buffer.unfix(sibling, tx);
            path.descend(page);
        } else {
            buffer.unfix(page, tx);
            path.descend(sibling);
        }
    }

    /**
     * @return the index of the first entry that goes to the right page. That
     * is, that many entries will remain in the left page.
     */
    protected int findSplitPosition(OMDPage<K, V, L> page) {
        int pos = 0;
        if (splitType == SplitType.LINEAR_SPLIT) {
            pos = findSplitPositionLinearSplit(page);
        } else if (splitType == SplitType.HALF) {
            pos = page.getEntryCount() / 2;
        } else if (splitType == SplitType.OPTIMAL) {
            pos = findOptimalSplitPos(page);
            return pos;
        } else {
            throw new IllegalStateException("Split type unrecognized: " + splitType);
        }
        // We also need to move all other pages with the same coordinate;
        // either to the left or to the right.
        pos = adjustSplitPoint(page, pos);
        return pos;
    }

    protected boolean goesToLeft(MBR<K> mbr, MBR<K> pageMBR) {
        K leftDist = mbr.getLow(0).subtract(pageMBR.getLow(0)).abs();
        K rightDist = mbr.getHigh(0).subtract(pageMBR.getHigh(0)).abs();
        // return leftDist < rightDist;
        return leftDist.compareTo(rightDist) < 0;
    }

    /**
     * @return a goodness value for the given split
     */
    protected float getSplitValue(MBR<K> p1, MBR<K> p2) {
        if (p1.overlaps(p2))
            return p1.countOverlapArea(p2).toFloat();
        return -p1.countEnlargement(p2).toFloat();
    }

    protected int findOptimalSplitPos(OMDPage<K, V, L> page) {
        int min = page.getSplitTolerance();
        int max = page.getPageEntryCapacity() - page.getSplitTolerance();
        assert min < max : min + " >= " + max;

        int possibilities = max - min + 1;

        Array<MBR<K>> fromRightMBRs = new Array<MBR<K>>(possibilities);
        // Calculate MBRs from right
        {
            MBR<K> fromRight = null;
            Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> contentIt = page
                .descendingContentIterator();
            for (int c = page.getPageEntryCapacity() - 1; c >= min; c--) {
                // Position c; cur is the MBR at position c
                MBR<K> cur = contentIt.next().getSecond().getFirst();
                fromRight = fromRight != null ? fromRight.extend(cur) : cur;
                if (c <= max) {
                    fromRightMBRs.put(c - min, cur);
                }
            }
        }

        // Calculate best position
        int selected = -1;
        {
            float bestVal = 0;
            MBR<K> fromLeft = null;
            Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> contentIt = page.contentIterator();
            for (int pos = 0; pos <= max; pos++) {
                // Position pos; cur is the MBR at position pos
                MBR<K> cur = contentIt.next().getSecond().getFirst();
                fromLeft = fromLeft != null ? fromLeft.extend(cur) : cur;

                if (pos < min || !isValidSplitPoint(page, pos))
                    continue;
                float splitValue = getSplitValue(cur, fromRightMBRs.get(pos - min));
                if (selected < 0 || splitValue < bestVal) {
                    bestVal = splitValue;
                    selected = pos;
                }
            }
        }
        if (selected < 0)
            throw new NotImplementedException("Cannot select split position");

        return selected;
    }

    /**
     * @return the optimal split point by linear split
     */
    protected int findSplitPositionLinearSplit(OMDPage<K, V, L> page) {
        MBR<K> pageMBR = page.getPageMBR();
        int splitPoint = -1;
        // Find the first entry that should go to the right page
        for (Pair<L, Pair<MBR<K>, PageValue<?>>> p : Iterables.get(page.contentIterator())) {
            MBR<K> entryMBR = p.getSecond().getFirst();
            splitPoint++;
            if (!goesToLeft(entryMBR, pageMBR)) {
                // This is the first entry that goes to the right page
                break;
            }
        }
        if (splitPoint >= page.getEntryCount()) {
            log.warn("No separator found from page, should this be possible?");
            splitPoint = page.getEntryCount() / 2;

        }
        return splitPoint;
    }

    /**
     * Checks that the split is possible at this split point. The split may
     * not be possible if the previous entry has the same coordinate than the
     * split point; or if too few entries are on either side of the split, and
     * so on.
     * 
     * @param splitPoint the index of the first entry that should go to the
     * right page
     * @return the adjusted split point
     */
    protected int adjustSplitPoint(OMDPage<K, V, L> page, int splitPoint) {
        int min = page.getSplitTolerance();
        int max = page.getPageEntryCapacity() - page.getSplitTolerance();
        assert min < max : min + " >= " + max;
        assert page.getEntryCount() > 2 * min : page.getEntryCount() + " cannot be split to "
            + min;

        // Restrict the splitPoint between [min,max]
        splitPoint = Math.max(min, splitPoint);
        splitPoint = Math.min(max, splitPoint);

        // Check if this is a valid split point
        if (isValidSplitPoint(page, splitPoint))
            return splitPoint;

        // Find the closest valid split point
        L orgSplitC = page.getEntryAt(splitPoint).getFirst();
        // Find the first valid point to the left (where coordinates differ)
        int leftSplit = splitPoint - 1;
        while (leftSplit >= 0 && page.getEntryAt(leftSplit).getFirst().equals(orgSplitC))
            leftSplit--;

        int rightSplit = splitPoint + 1;
        while (rightSplit < page.getEntryCount()
            && page.getEntryAt(rightSplit).getFirst().equals(orgSplitC))
            rightSplit++;

        if (leftSplit < 0 && rightSplit >= page.getEntryCount()) {
            throw new NotImplementedException(
                "Page contains only entries with the same coordinate");
        }

        if (rightSplit >= page.getEntryCount() || leftSplit > (page.getEntryCount() - rightSplit)) {
            // Select left split
            splitPoint = leftSplit + 1;
        } else {
            // Select right split
            splitPoint = rightSplit;
        }
        assert isValidSplitPoint(page, splitPoint) : splitPoint
            + " is not a valid split point for " + page;
        return splitPoint;
    }

    /**
     * Checks if the page can be split at the given index (so that the search
     * keys are split into disjoint ranges).
     */
    private boolean isValidSplitPoint(OMDPage<K, V, L> page, int splitPoint) {
        // Cannot split at first or last index, it would not be a split
        if (splitPoint < 1 || splitPoint >= page.getEntryCount() - 1)
            return false;

        L splitC = page.getEntryAt(splitPoint).getFirst();
        L prevC = page.getEntryAt(splitPoint - 1).getFirst();

        // Previous entry coordinate must not be the same as the split point
        // coordinate
        return !prevC.equals(splitC);
    }

    /**
     * Moves some of the entries from an overflowing page into a newly created
     * empty page.
     * 
     * @param fromPage the page to move entries from
     * @param toPage the page where the entries are moved to
     * @param splitPos the index of the first entry that goes to the right
     * page. That is, splitPos entries will remain in the left page.
     * @return the separator key, e.g. the last key not moved to toPage
     */
    private L moveSomePages(OMDPage<K, V, L> fromPage, OMDPage<K, V, L> toPage, int splitPos) {
        // Move half of the children from this page into the new page
        int index = 0;

        L lastKey = null;
        fromPage.setDirty(true);
        toPage.setDirty(true);

        for (Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> it = fromPage.contentIterator(); it
            .hasNext();) {

            Pair<L, Pair<MBR<K>, PageValue<?>>> entry = it.next();

            // Skip first half entries
            if (++index > splitPos) {
                PageValue<?> value = entry.getSecond().getSecond();

                MBR<K> mbr = entry.getSecond().getFirst();
                // Move entry from this page to the new page
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Moving child %s (mbr %s) from %s to %s", value, mbr,
                        fromPage.getName(), toPage.getName()));
                }

                toPage.putContents(entry.getFirst(), mbr, value);
                it.remove();
            } else {
                // Track the last key
                lastKey = entry.getFirst();
            }
        }

        // Recalculate MBRs
        fromPage.recalculateMBR();
        toPage.recalculateMBR();
        if (log.isDebugEnabled()) {
            log.debug("New MBRS: " + fromPage.getPageMBR() + ", " + toPage.getPageMBR());
            log.debug("The last key not moved is " + lastKey);
        }
        return lastKey;
    }

    /**
     * Deletes a key from this leaf page. The key is guaranteed to be either
     * in this page or in the overflow page. Expects the pages in the path to
     * be latched. Upon return, the path is latched; the possibly checked
     * overflow pages have been released.
     * 
     * @param page the leaf page
     * @param key the key to delete
     * @return the associated value
     */
    public V delete(PagePath<MBR<K>, V, OMDPage<K, V, L>> path, MBR<K> mbr,
        Transaction<MBR<K>, V> tx) {
        OMDPage<K, V, L> page = path.getCurrent();
        assert page.isLeafPage();

        if (page.contains(mbr)) {
            // We have found the value
            return deleteFromPage(path, null, mbr, tx);
        }

        // MBR not found on this leaf page
        return null;
    }

    /**
     * The correct node is either path.getCurrent() (if overflowPageList is
     * null), or the last entry in the overflowPageList.
     * 
     * This method should do the maintenance actions such as moving entries
     * from the overflow page(s) back to the original page; or similar.
     * 
     * @param path the path to the leaf node, from root
     * @param overflowPageList list of overflow pages traversed; the correct
     * key was found in the last page in this list
     * @param mbr the mbr to delete
     * @return the value deleted
     */
    @SuppressWarnings("unchecked")
    public V deleteFromPage(PagePath<MBR<K>, V, OMDPage<K, V, L>> path,
        List<OMDPage<K, V, L>> overflowPageList, MBR<K> mbr, Transaction<MBR<K>, V> tx) {
        OMDPage<K, V, L> page = path.getCurrent();
        assert page.isLeafPage();

        if (overflowPageList != null)
            throw new NotImplementedException("Deleting from overflow pages is not implemented");

        V value = (V) page.removeContents(tree.getSearchKey(mbr), mbr);

        // First, recalculate MBRs along the path from leaf to root.
        // For this to work, a page must never become totally empty, so page
        // min entries must be > 1.
        recalculateMBRs(path);
        // Then, check for underflow. This cannot lead to wrong MBR sizes
        // higher up (so no need to recalculateMBRs), because each merge
        // operation maintains the parent MBR (unioning two MBRs that are
        // already part of the parent).
        checkUnderflow(path, tx);
        return value;
    }

    /**
     * Recalculated the MBRs on the path bottom-up.
     */
    public void recalculateMBRs(PagePath<MBR<K>, V, OMDPage<K, V, L>> path) {
        for (Pair<OMDPage<K, V, L>, OMDPage<K, V, L>> pp : Iterables.get(path.bottomUpIterator())) {
            OMDPage<K, V, L> page = pp.getFirst();
            OMDPage<K, V, L> parent = pp.getSecond();
            // A page may have become empty (in case it was the root page)
            // we'll want to skip that
            if (page.getEntryCount() > 0) {
                MBR<K> cur = page.getPageMBR();
                page.recalculateMBR();
                MBR<K> newMBR = page.getPageMBR();
                // If page MBR has not changed, and the router at parent has
                // not changed, then we can stop updating here
                if (newMBR.equals(cur)
                    && (parent == null || parent.getRouter(page.getSearchKey()).getFirst()
                        .equals(newMBR))) {
                    // No change
                    return;
                }
                if (parent != null) {
                    // Update parent and continue
                    parent.updateMBR(page);
                }
            }
        }
    }

    /**
     * Checks and fixes underflow at a root page.
     * 
     * @param root the root page
     */
    @Override
    protected OMDPage<K, V, L> checkRootUnderflow(PagePath<MBR<K>, V, OMDPage<K, V, L>> path,
        Transaction<MBR<K>, V> tx) {
        OMDPage<K, V, L> root = path.getCurrent();
        assert root.isRoot(path);
        assert path.size() == 1;
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
            PageID childID = (PageID) root.getEntries().get(0);
            OMDPage<K, V, L> child = buffer.fixPage(childID, factory, false, tx);
            assert child != null;
            // child.detach(root);
            root.removeContents(child.getSearchKey(), child.getPageID());
            assert root.getEntryCount() == 0 : String
                .format("Children not empty after only child moved to new tree root");
            // Replace root contents with child page contents
            root.format(child.getHeight());
            child.moveAllEntries(root, null);

            // Delete the child node
            buffer.delete(child, tx);
            return root;
        }
        if (log.isDebugEnabled())
            log.debug("Path is " + path);
        return root;
    }

    /**
     * Merges an underflown page with a sibling page. At entry: page is fixed
     * to buffer. Before moving to moveAllAndDetach, page + sibling is fixed.
     * After moveAllAndDetach, the resulting page in path is fixed.
     * 
     * @param page the page to merge with a sibling
     */
    @Override
    public void merge(PagePath<MBR<K>, V, OMDPage<K, V, L>> path, Transaction<MBR<K>, V> tx,
        MBR<K> key) {
        OMDPage<K, V, L> page = path.getCurrent();
        OMDPage<K, V, L> parent = path.getParent();
        assert parent != null : "Trying to merge() a root page " + page.getName();

        if (log.isDebugEnabled())
            log.debug(String.format("Merging page %s with some sibling", page.getName()));
        // If the sibling page immediately to the right of the deficient page
        // has more than the minimum number of elements, choose the median of
        // the separator and the values in both pages as the new separator and
        // put that in the parent.
        OMDPage<K, V, L> rightSibling = parent.getHigherSibling(page.getSearchKey(), page
            .getPageMBR(), page.getPageID(), tx);
        if (rightSibling != null && rightSibling.getEntryCount() > page.getMergeAtEntries()) {
            // More than minimum amount of entries, so we can redistribute
            // values directly between the pages
            // Sets the involved pages dirty
            redistributeEntries(page, rightSibling, parent);
            buffer.unfix(rightSibling, tx);
            return;
        }
        OMDPage<K, V, L> leftSibling = parent.getLowerSibling(page.getSearchKey(), page
            .getPageMBR(), page.getPageID(), tx);
        assert page != leftSibling : "Found same left sibling for key " + page.getKeyRange()
            + " in " + parent.getEntries();
        if (leftSibling != null && leftSibling.getEntryCount() > page.getMergeAtEntries()) {
            if (rightSibling != null) {
                // Release right sibling (not used)
                buffer.unfix(rightSibling, tx);
            }
            // More than the minimum, redistribute values between these pages
            // Sets the involved pages dirty
            redistributeEntries(leftSibling, page, parent);
            buffer.unfix(leftSibling, tx);
            return;
        }

        // First try with left sibling
        OMDPage<K, V, L> sibling = leftSibling;
        boolean pathPageIsLeft = false;
        if (sibling == null) {
            // Then choose right sibling
            sibling = rightSibling;
            pathPageIsLeft = true;
        } else if (rightSibling != null) {
            // Left sibling chosen, unfix the right sibling
            buffer.unfix(rightSibling, tx);
        }
        assert sibling != null : "No sibling found for " + page + " in " + parent.getEntries();

        moveAllAndDetach(path, sibling, pathPageIsLeft, tx);
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
    protected void redistributeEntries(OMDPage<K, V, L> page, OMDPage<K, V, L> sibling,
        OMDPage<K, V, L> parent) {
        if (log.isDebugEnabled())
            log.debug(String.format("Redistributing entries between pages %s < %s", page
                .getName(), sibling.getName()));
        page.setDirty(true);
        sibling.setDirty(true);

        if (page.getEntryCount() < page.getMinEntries()) {
            // Case 1. this page has too few entries
            // Move entries from sibling to this page
            while (Math.abs(page.getEntryCount() - sibling.getEntryCount()) >= 2) {
                // Take first entry from sibling, and add it here
                sibling.moveFirst(page);
            }
        } else {
            // Case 2. the sibling has too few entries
            // Move entries from this page to the sibling page
            while (Math.abs(page.getEntryCount() - sibling.getEntryCount()) >= 2) {
                // Take last entry from here, and move it to the sibling
                page.moveLast(sibling);
            }
        }

        adjustSplitPosition(page, sibling);
        L separator = page.getLastEntry().getFirst();

        // Update key ranges in the pages and the parent
        assert separator != null : String.format(
            "No entries moved when redistributing pages %s and %s", page.getName(), sibling
                .getName());

        // Sibling must have higher keys; therefore sibling's search key will
        // not change
        page.recalculateMBR();
        sibling.recalculateMBR();

        // This method also updates the page's own search key; and the MBR
        // at the router
        parent.updateSearchKey(page, separator);

        // Update the router MBR of the sibling (at the parent)
        parent.updateMBR(sibling);
    }

    /**
     * Adjusts the split position so that the last entry of page is not the
     * same as the first entry of sibling.
     */
    private void adjustSplitPosition(OMDPage<K, V, L> page, OMDPage<K, V, L> sibling) {
        if (!page.getLastEntry().getFirst().equals(sibling.getFirstEntry().getFirst()))
            return;
        L problemEntry = sibling.getFirstEntry().getFirst();
        Holder<Boolean> toSibling = new Holder<Boolean>();
        // Find out which page contains less of the problematic keys
        {
            Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> pIt = page.descendingContentIterator();
            Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> sIt = sibling.contentIterator();
            while (pIt.hasNext() && sIt.hasNext()) {
                Pair<L, Pair<MBR<K>, PageValue<?>>> pEntry = pIt.next();
                Pair<L, Pair<MBR<K>, PageValue<?>>> sEntry = sIt.next();
                if (!pEntry.getFirst().equals(problemEntry)) {
                    // Page contains a different entry, move entries from page
                    // to
                    // sibling
                    toSibling.setValue(true);
                    break;
                }
                if (!sEntry.getFirst().equals(problemEntry)) {
                    // Sibling contains a different entry, move entries from
                    // sibling to page
                    toSibling.setValue(false);
                    break;
                }
            }
        }
        if (!toSibling.isInitialized()) {
            throw new NotImplementedException("This special case not implemented");
        }
        if (toSibling.getValue()) {
            // Move from page to sibling
            Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> pIt = page.descendingContentIterator();
            while (pIt.hasNext()) {
                Pair<L, Pair<MBR<K>, PageValue<?>>> moveEntry = pIt.next();
                if (!moveEntry.getFirst().equals(problemEntry)) {
                    // We are done
                    return;
                }
                sibling.putContents(moveEntry.getFirst(), moveEntry.getSecond().getFirst(),
                    moveEntry.getSecond().getSecond());
                pIt.remove();
            }
        } else {
            // Move from sibling to page
            Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> sIt = sibling.contentIterator();
            while (sIt.hasNext()) {
                Pair<L, Pair<MBR<K>, PageValue<?>>> moveEntry = sIt.next();
                if (!moveEntry.getFirst().equals(problemEntry)) {
                    // We are done
                    return;
                }
                page.putContents(moveEntry.getFirst(), moveEntry.getSecond().getFirst(),
                    moveEntry.getSecond().getSecond());
                sIt.remove();
            }
        }
    }

    /**
     * Moves all entries from the pages into the rightmost one of them, and
     * detaches the leftmost one from the tree.
     * 
     * Entry: page(path.getCurrent) + sibling are fixed. At exit: only the
     * page(path.getCurrent) is fixed.
     * 
     * Sets both siblings dirty
     * 
     * @param page a page
     * @param sibling the page's sibling (either left or right)
     * @param pathPageIsLeft true when the page on path is the leftmost page
     * @eturn the page that is left alive
     */
    private OMDPage<K, V, L> moveAllAndDetach(PagePath<MBR<K>, V, OMDPage<K, V, L>> path,
        OMDPage<K, V, L> sibling, boolean pathPageIsLeft, Transaction<MBR<K>, V> tx) {
        OMDPage<K, V, L> page = path.getCurrent();
        assert sibling != null;

        OMDPage<K, V, L> first = null;
        OMDPage<K, V, L> second = null;

        // Second page will be deleted
        if (pathPageIsLeft) {
            first = sibling;
            second = page;
        } else {
            first = page;
            second = sibling;
        }
        // Both closest sibling pages contain the minimum amount of pages
        // Move all values from the second page into the first page
        if (log.isDebugEnabled())
            log.debug(String.format("Moving all entries from %s to %s", second.getName(), first
                .getName()));

        // First contains the lower keys
        OMDPage<K, V, L> parent = path.ascend();
        // Move all entries from second to first
        // Dirties all involved pages
        second.moveAllEntries(first, parent);

        // Delete this page from the tree
        parent.removeContents(second.getSearchKey(), second.getPageID());

        first.recalculateMBR();
        parent.updateMBR(first);

        // Delete the page from the page buffer (requires a single fix, which
        // is also released)
        buffer.delete(second.getPageID(), tx);

        // Check that parent has not underflown
        PageID firstID = first.getPageID();
        buffer.unfix(firstID, tx);

        OMDPage<K, V, L> updatedFirst = checkUnderflow(path, tx);
        path.descend(updatedFirst);
        return updatedFirst;
    }

}
