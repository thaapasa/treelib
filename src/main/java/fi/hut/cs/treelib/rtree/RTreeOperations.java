package fi.hut.cs.treelib.rtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.AbstractTreeOperations;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.hut.cs.treelib.util.MBRPredicate;
import fi.hut.cs.treelib.util.MDDBUtils;
import fi.tuska.util.Array;
import fi.tuska.util.Callback;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Pair;
import fi.tuska.util.iterator.Iterables;

public class RTreeOperations<K extends Key<K>, V extends PageValue<?>> extends
    AbstractTreeOperations<MBR<K>, V, RTreePage<K, V>> {

    private static final Logger log = Logger.getLogger(RTreeOperations.class);

    public enum SplitType {
        LINEAR_SPLIT, R_STAR
    };

    private SplitType splitType = SplitType.LINEAR_SPLIT;

    private RTree<K, V> tree;

    protected RTreeOperations(RTree<K, V> tree) {
        super(tree);
        this.tree = tree;
    }

    public void setSplitType(SplitType splitType) {
        this.splitType = splitType;
    }

    @Override
    protected RTree<K, V> getTree() {
        return tree;
    }

    @Override
    protected boolean isAllowDuplicates() {
        // J-tree supports duplicate entries
        return true;
    }

    @Override
    public void findPathToLeafPage(PageID rootPageID, MBR<K> mbr,
        PagePath<MBR<K>, V, RTreePage<K, V>> path, int transactionID, Owner owner) {
        assert path.isEmpty();

        RTreePage<K, V> root = buffer.fixPage(rootPageID, factory, false, owner);
        MBRPredicate<K> pred = new MBRPredicate<K>(mbr, true, 1);

        root.findPathDepthFirst(pred, new Callback<Page<MBR<K>, V>>() {
            @Override
            public boolean callback(Page<MBR<K>, V> page) {
                // false to stop search, true to continue search
                return false;
            }
        }, path, owner);
        buffer.unfix(root, owner);
    }

    public PagePath<MBR<K>, V, RTreePage<K, V>> findPathForDelete(PageID rootPageID,
        final MBR<K> mbr, Owner owner) {
        RTreePage<K, V> root = buffer.fixPage(rootPageID, factory, false, owner);
        MBRPredicate<K> pred = new MBRPredicate<K>(mbr, true, 1);
        // findPathDepthFirst takes a fix for root too, so unlog one fix
        tree.getStatisticsLogger().unlog(Operation.OP_BUFFER_FIX);
        PagePath<MBR<K>, V, RTreePage<K, V>> path = new PagePath<MBR<K>, V, RTreePage<K, V>>(true);
        root.findPathDepthFirst(pred, new Callback<Page<MBR<K>, V>>() {
            @Override
            public boolean callback(Page<MBR<K>, V> page) {
                // false to stop search, true to continue search
                return !page.contains(mbr);
            }
        }, path, owner);
        buffer.unfix(root, owner);
        return path;
    }

    @SuppressWarnings("unchecked")
    public V delete(MBR<K> mbr, PageID rootPageID, Transaction<MBR<K>, V> tx) {
        PagePath<MBR<K>, V, RTreePage<K, V>> path = findPathForDelete(rootPageID, mbr, tx);
        if (path == null)
            return null;

        RTreePage<K, V> page = path.getCurrent();
        V value = (V) page.removeContents(mbr);

        consolidate(path, tx);
        buffer.unfix(path, tx);

        return value;
    }

    /**
     * Consolidates pages bottom-up, and also recalculates MBRs along the
     * path.
     */
    protected void consolidate(PagePath<MBR<K>, V, RTreePage<K, V>> path,
        Transaction<MBR<K>, V> tx) {
        RTreePage<K, V> page = path.getCurrent();
        RTreePage<K, V> parent = path.ascend();
        if (page.getEntryCount() == 0) {
            if (parent == null) {
                // Just unfix the extra fix on the page
                buffer.unfix(page, tx);
                // Last entry
                assert path.isEmpty();
                // Delete this root page from the tree
                tree.deleteRoot(page, tx);
                return;
            } else {
                // Remove this page from the parent
                boolean res = parent.removeContents(page.getPageMBR(), page.getPageID());
                assert res : page.getPageMBR() + " - " + page.getPageID();
                // Delete this page, will unfix the page
                buffer.delete(page.getPageID(), tx);

                consolidate(path, tx);
                return;
            }
        }
        page.recalculateAndUpdateMBR(parent);
        if (!path.isEmpty()) {
            consolidate(path, tx);
        }

        // Descend back to page
        path.descend(page);
    }

    @Override
    public PagePath<MBR<K>, V, RTreePage<K, V>> findPathForInsert(PageID rootPageID, MBR<K> mbr,
        PagePath<MBR<K>, V, RTreePage<K, V>> path, Transaction<MBR<K>, V> tx) {
        if (path == null) {
            path = new PagePath<MBR<K>, V, RTreePage<K, V>>(true);
        } else {
            if (!path.isEmpty()) {
                buffer.unfix(path, tx);
            }
        }
        RTreePage<K, V> page = buffer.fixPage(rootPageID, factory, false, tx);
        path.attachRoot(page);

        while (true) {
            // extendPageMBR checks if the page MBR already covers the
            // given MBR
            page.extendPageMBR(mbr);

            // Stop search after the leaf node has been reached and the leaf
            // page MBR has been extended
            if (page.isLeafPage())
                return path;

            Pair<MBR<K>, PageID> entry = page.findEntryAndEnlarge(mbr);
            PageID childID = entry.getSecond();
            assert childID != null : page + " for mbr " + mbr;

            RTreePage<K, V> child = buffer.fixPage(childID, factory, false, tx);
            path.descend(child);
            page = child;
        }
    }

    /**
     * Overridden: key ranges handled differently (MBRs here instead of key
     * ranges)
     */
    @Override
    protected void increaseTreeHeight(RTreePage<K, V> oldRoot, MBR<K> key, PageID pageID,
        PagePath<MBR<K>, V, RTreePage<K, V>> path, Transaction<MBR<K>, V> tx) {
        assert oldRoot == path.getCurrent();
        assert oldRoot.isRoot() : String.format(
            "Page %s is not root page, cannot increase height", oldRoot.getName());
        path.ascend();
        assert (path.isEmpty());

        // Dirties the old root
        oldRoot.setRoot(false);

        if (log.isDebugEnabled())
            log.debug(String.format("Increasing tree height from page %s", oldRoot.getName()));

        // Create new root page
        RTreePage<K, V> newRoot = tree.createIndexRoot(oldRoot.getHeight() + 1, tx);

        // Move the old root page under the new root.
        MBR<K> oldRootMBR = oldRoot.getPageMBR();
        newRoot.putContents(oldRootMBR, oldRoot.getPageID());

        // The root is fixed for use, so it can be put into the path
        path.attachRoot(newRoot);
        path.descend(oldRoot);

        // Split this page (this page is no longer a root page)
        split(path, key, pageID, true, tx);
    }

    @Override
    protected void splitSpaceEnsured(RTreePage<K, V> page, MBR<K> key, PageID pageID,
        PagePath<MBR<K>, V, RTreePage<K, V>> path, Transaction<MBR<K>, V> tx) {
        assert page == path.getCurrent();
        RTreePage<K, V> parent = path.getParent();
        // assert parent.containsChild(page);

        StatisticsLogger stats = tree.getStatisticsLogger();
        // Create a sibling page
        RTreePage<K, V> sibling = tree.createSiblingPage(page, tx);

        log.debug("Splitting page " + page + " to sibling " + sibling);

        // Sets the siblings dirty
        separateEntries(page, sibling);
        MBR<K> myNewMBR = page.getPageMBR();
        MBR<K> siblingMBR = sibling.getPageMBR();

        // True when the new key will be added to this page; false when it
        // will go to the sibling page
        boolean addToThis = true;
        // First check if pageID is set; if it is, use that to select the page
        if (pageID != null ? page.containsChild(pageID) : myNewMBR.countEnlargement(key)
            .compareTo(siblingMBR.countEnlargement(key)) < 0) {
            myNewMBR = myNewMBR.extend(key);
            page.setPageMBR(myNewMBR);
        } else {
            siblingMBR = siblingMBR.extend(key);
            sibling.setPageMBR(siblingMBR);
            addToThis = false;
        }

        // Sets the parent dirty
        parent.updateKeyRange(page, myNewMBR);

        // Attach sibling to parent + update sibling key range
        parent.putContents(siblingMBR, sibling.getPageID());

        MDDBUtils.logSplit(stats, page.getPageMBR(), sibling.getPageMBR(), page.getEntryCount()
            / (double) page.getPageEntryCapacity());

        path.ascend();
        if (addToThis) {
            // Adding to this page
            buffer.unfix(sibling, tx);
            path.descend(page);
        } else {
            // Adding to sibling page
            buffer.unfix(page, tx);
            path.descend(sibling);
        }
    }

    /**
     * Separates entries by moving about half of them from a filled page into
     * a newly created sibling page.
     * 
     * It is enough to recalculate the MBRs of the pages; the calling code
     * will update the parent pointers and add a pointer to the sibling page.
     * 
     * @param fromPage the page to move entries from
     * @param toPage the page where the entries are moved to
     */
    private void separateEntries(RTreePage<K, V> fromPage, RTreePage<K, V> toPage) {
        assert toPage.getEntryCount() == 0;
        assert fromPage.getFreeEntries() == 0;
        if (splitType == SplitType.R_STAR) {
            log.debug("Using R*-split");
            rStarSplit(fromPage, toPage);
        } else if (splitType == SplitType.LINEAR_SPLIT) {
            log.debug("Using new linear split");
            linearSplit(fromPage, toPage);
        } else {
            throw new UnsupportedOperationException("Split type not recognized: " + splitType);
        }
    }

    /**
     * Implements the new linear split.
     */
    private void linearSplit(RTreePage<K, V> fromPage, RTreePage<K, V> toPage) {
        List<Pair<MBR<K>, Integer>> mbrs = getLinearSplitList(fromPage, fromPage
            .getSplitTolerance());
        moveAllEntries(fromPage, toPage, CollectionUtils.getPairSecondList(mbrs));
    }

    /**
     * Moves all entries that are in the list from the source page to the
     * target page.
     */
    private void moveAllEntries(RTreePage<K, V> fromPage, RTreePage<K, V> toPage,
        List<Integer> entries) {

        Collections.sort(entries);
        int c = 0;
        Integer nextToMove = entries.remove(0);
        for (Iterator<Pair<MBR<K>, PageValue<?>>> i = fromPage.contentIterator(); i.hasNext()
            && nextToMove != null;) {
            Pair<MBR<K>, PageValue<?>> entry = i.next();
            if (c == nextToMove) {
                toPage.putContents(entry.getFirst(), entry.getSecond());
                i.remove();
                nextToMove = entries.isEmpty() ? null : entries.remove(0);
            }
            c++;
        }
        fromPage.recalculateMBR();
        toPage.recalculateMBR();
    }

    private void balanceLists(List<Pair<MBR<K>, Integer>> list1,
        List<Pair<MBR<K>, Integer>> list2, int min) {
        while (list1.size() < min) {
            list1.add(list2.remove(list2.size() - 1));
        }
        while (list2.size() < min) {
            list2.add(list1.remove(list1.size() - 1));
        }
    }

    private List<Pair<MBR<K>, Integer>> getLinearSplitList(RTreePage<K, V> fromPage,
        int minEntriesPerList) {
        int initialSize = fromPage.getPageEntryCapacity();
        List<Pair<MBR<K>, Integer>> listL = new ArrayList<Pair<MBR<K>, Integer>>(initialSize);
        List<Pair<MBR<K>, Integer>> listR = new ArrayList<Pair<MBR<K>, Integer>>(initialSize);
        List<Pair<MBR<K>, Integer>> listT = new ArrayList<Pair<MBR<K>, Integer>>(initialSize);
        List<Pair<MBR<K>, Integer>> listB = new ArrayList<Pair<MBR<K>, Integer>>(initialSize);

        MBR<K> pageMBR = fromPage.getPageMBR();

        int c = 0;
        for (Pair<MBR<K>, PageValue<?>> entry : Iterables.get(fromPage.contentIterator())) {
            MBR<K> mbr = entry.getFirst();
            assert mbr != null;

            // [A] if xl - L < R - xh
            if (mbr.getLow(0).subtract(pageMBR.getLow(0)).compareTo(
                pageMBR.getHigh(0).subtract(mbr.getHigh(0))) < 0)
                listL.add(new Pair<MBR<K>, Integer>(mbr, c));
            else
                listR.add(new Pair<MBR<K>, Integer>(mbr, c));

            // [A] if yl - B < T - xh
            if (mbr.getLow(1).subtract(pageMBR.getLow(1)).compareTo(
                pageMBR.getHigh(1).subtract(mbr.getHigh(1))) < 0)
                listB.add(new Pair<MBR<K>, Integer>(mbr, c));
            else
                listT.add(new Pair<MBR<K>, Integer>(mbr, c));

            c++;
        }

        balanceLists(listL, listR, minEntriesPerList);
        balanceLists(listT, listB, minEntriesPerList);

        // [A] if max(|listL|,|listR|) < max(|listB|,|listT|), split along x
        int xSplitSize = Math.max(listL.size(), listR.size());
        int ySplitSize = Math.max(listB.size(), listT.size());
        if (xSplitSize < ySplitSize)
            return listR;
        if (ySplitSize < xSplitSize)
            return listB;

        // [A] Tie breaker

        // [A] if overlap(listL, listR) < overlap(listB, listT) then split
        // along x
        MBR<K> mbrL = getListMBR(listL);
        MBR<K> mbrR = getListMBR(listR);
        MBR<K> mbrT = getListMBR(listT);
        MBR<K> mbrB = getListMBR(listB);
        float xOverlap = mbrL.countOverlapArea(mbrR).toFloat();
        float yOverlap = mbrB.countOverlapArea(mbrT).toFloat();
        if (xOverlap < yOverlap)
            return listR;
        if (yOverlap < xOverlap)
            return listB;

        // [A] else split node along the direction with smallest total
        // coverage

        // TODO: Is this what is meant with the previous sentence?
        float coverageX = mbrL.getArea().toFloat() + mbrR.getArea().toFloat();
        float coverageY = mbrT.getArea().toFloat() + mbrB.getArea().toFloat();
        return coverageX < coverageY ? listR : listB;
    }

    private MBR<K> getListMBR(List<Pair<MBR<K>, Integer>> list) {
        MBR<K> total = null;
        for (Pair<MBR<K>, Integer> entry : list) {
            total = total != null ? total.extend(entry.getFirst()) : entry.getFirst();
        }
        return total;
    }

    /**
     * Implements the R*-tree split.
     */
    private void rStarSplit(RTreePage<K, V> fromPage, RTreePage<K, V> toPage) {
        List<Pair<MBR<K>, Integer>> mbrs = getMBRS(fromPage);
        // [B] S1: Invoke ChooseSplitAxis to determine the axis, perpendicular
        // to which the split is performed
        // [B] S2: Invoke ChooseSplitIndex to determine the best distribution
        // into two groups along that axis
        Pair<Integer, Integer> m = rStarSplitAxis(mbrs, fromPage.getSplitTolerance());
        int axis = m.getFirst();

        MDDBUtils.logSplitAxis(tree.getStatisticsLogger(), axis);

        int splitPos = m.getSecond();
        log.debug("Best split along axis " + axis + "; at pos " + splitPos);
        // [B] S3: Distribute the entries into two groups
        // Sort MBRs by the selected axis
        sortMBRsByAxis(mbrs, axis);

        // Move all entries from 0 to splitPos (including the entry with index
        // splitPos) from the MBR list to the target page
        moveAllEntries(fromPage, toPage, CollectionUtils.getPairSecondList(mbrs).subList(0,
            splitPos + 1));
    }

    private List<Pair<MBR<K>, Integer>> getMBRS(RTreePage<K, V> page) {
        List<Pair<MBR<K>, Integer>> mbrs = new ArrayList<Pair<MBR<K>, Integer>>(page
            .getEntryCount());

        int c = 0;
        for (Pair<MBR<K>, PageValue<?>> entry : Iterables.get(page.contentIterator())) {
            mbrs.add(new Pair<MBR<K>, Integer>(entry.getFirst(), c));
            c++;
        }
        return mbrs;
    }

    private void sortMBRsByAxis(List<Pair<MBR<K>, Integer>> mbrList, final int axis) {
        Collections.sort(mbrList, new Comparator<Pair<MBR<K>, Integer>>() {
            // Compare first by the lower values of the selected axis,
            // then by the higher values
            @Override
            public int compare(Pair<MBR<K>, Integer> p1, Pair<MBR<K>, Integer> p2) {
                MBR<K> m1 = p1.getFirst();
                MBR<K> m2 = p2.getFirst();
                int c = m1.getLow(axis).compareTo(m2.getLow(axis));
                if (c != 0)
                    return c;
                c = m1.getHigh(axis).compareTo(m2.getHigh(axis));
                if (c != 0)
                    return c;
                // Among identical MBR's, sort by the position values
                return p1.getSecond().compareTo(p2.getSecond());
            }
        });
    }

    /**
     * @return pair: the sum of perimeters of all possible splits along this
     * axis; and the split position with minimum overlap area
     */
    private Pair<Double, Integer> findRStarMinSplitPos(List<Pair<MBR<K>, Integer>> list,
        int minMBRSPerGroup) {
        final int min = minMBRSPerGroup;
        final int max = list.size() - minMBRSPerGroup;
        assert min >= 2;
        assert max > min : max + " <= " + min + " (min/grp: " + minMBRSPerGroup + ", listsize: "
            + list.size() + ")";

        // Precalculate the MBRs of the rightmost groups
        // reverseMBR[i] contains the MBR of all elements with index k >= i
        Array<MBR<K>> reverseMBRs = new Array<MBR<K>>(list.size());
        MBR<K> curMBR = null;
        // Create the reverse MBR list backwards
        for (int i = list.size() - 1; i >= 0; i--) {
            MBR<K> elementMBR = list.get(i).getFirst();
            curMBR = curMBR != null ? curMBR.extend(elementMBR) : elementMBR;
            reverseMBRs.put(i, curMBR);
        }

        MBR<K> leftMBR = null;
        double totalS = 0;
        int minPos = -1;
        double minOverlapArea = 0;
        for (int i = 0; i < max; i++) {
            MBR<K> elementMBR = list.get(i).getFirst();
            leftMBR = leftMBR != null ? leftMBR.extend(elementMBR) : elementMBR;

            // leftMBR contains now the MBR of the left group, that is the
            // MBR of all elements with index k <= i
            if (i >= min) {
                // Test split with split at position i

                MBR<K> rightMBR = reverseMBRs.get(i + 1);

                // [B] Compute S, the sum of all margin-values of the
                // different distributions
                // In [B], margin-value means the (sum of the) perimeter
                // length of the MBRs
                float curS = leftMBR.getPerimeter().add(rightMBR.getPerimeter()).toFloat();
                totalS += curS;

                double overlapArea = leftMBR.countOverlapArea(rightMBR).toFloat();
                if (minPos < 0 || overlapArea < minOverlapArea) {
                    minPos = i;
                    minOverlapArea = overlapArea;
                }
            }
        }
        assert minPos >= min;
        assert minPos <= max;

        return new Pair<Double, Integer>(totalS, minPos);
    }

    /**
     * @return pair: selected axis (0 = X, 1 = Y, etc.); and split position
     * along that axis
     */
    private Pair<Integer, Integer> rStarSplitAxis(List<Pair<MBR<K>, Integer>> mbrs, int minEntries) {
        MBR<K> proto = tree.getKeyPrototype();
        double minS = Integer.MAX_VALUE;
        int selectedDim = -1;
        int selectedPos = -1;
        // 0 = X-axis, 1 = Y-axis, and so on
        // For each axis...
        for (int axis = 0; axis < proto.getDimensions(); axis++) {
            // Calculate curS for this axis

            // [B] Sort the entries by the lower and then by the upper value
            // of their rectangles and determine all distributions as
            // described above. Compute S, the sum of all margin-values of the
            // different distributions.
            sortMBRsByAxis(mbrs, axis);

            log.debug("Axis: " + axis + ", MBRs: " + mbrs);
            // Possible split types:
            Pair<Double, Integer> m = findRStarMinSplitPos(mbrs, minEntries);
            double curS = m.getFirst();
            log.debug("S-value: " + curS + ", best split at pos " + m.getSecond());

            // Update minimum encountered S
            if (selectedDim < 0 || curS < minS) {
                selectedDim = axis;
                minS = curS;
                selectedPos = m.getSecond();
            }
        }
        return new Pair<Integer, Integer>(selectedDim, selectedPos);
    }

    /**
     * This implements the B-tree style split which just moves some entries
     * from one page to the other.
     */
    @SuppressWarnings("unused")
    private void moveHalfEntries(RTreePage<K, V> fromPage, RTreePage<K, V> toPage) {
        // Move half of the children from this page into the new page
        int index = 0;
        int halfEntries = fromPage.getPageEntryCapacity() / 2;
        MBR<K> firstKey = null;
        fromPage.setDirty(true);
        toPage.setDirty(true);

        for (Iterator<Pair<MBR<K>, PageValue<?>>> it = fromPage.contentIterator(); it.hasNext();) {

            Pair<MBR<K>, PageValue<?>> entry = it.next();
            PageValue<?> value = entry.getSecond();
            // Skip first half entries
            if (++index > halfEntries) {
                if (firstKey == null) {
                    firstKey = entry.getFirst();
                }

                // Move entry from this page to the new page
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Moving child %s from %s to %s", value, fromPage
                        .getName(), toPage.getName()));
                }

                toPage.putContents(entry.getFirst(), value);
                it.remove();
            }
        }
        fromPage.recalculateMBR();
        toPage.recalculateMBR();
    }

    public boolean findExact(final MBR<K> mbr, final Callback<Pair<MBR<K>, V>> callback,
        Owner owner) {
        // Adds a fix to the root page
        RTreePage<K, V> root = tree.getRoot(owner);
        if (root == null)
            return true;

        // Unlog extra root log
        tree.getStatisticsLogger().unlog(Operation.OP_BUFFER_FIX);

        boolean res = root.traverseMDPages(new MBRPredicate<K>(mbr, true, 1),
            new Callback<Page<MBR<K>, V>>() {
                @Override
                public boolean callback(Page<MBR<K>, V> page) {
                    RTreePage<K, V> rPage = (RTreePage<K, V>) page;
                    return rPage.processMBREntries(new Callback<Pair<MBR<K>, PageValue<?>>>() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                            V val = (V) entry.getSecond();
                            if (mbr.equals(entry.getFirst())) {
                                if (callback != null) {
                                    boolean continueSearch = callback
                                        .callback(new Pair<MBR<K>, V>(entry.getFirst(), val));
                                    if (!continueSearch)
                                        return false;
                                }
                            }
                            return true;
                        }
                    });
                }
            }, owner);
        // Unfix the extra latch on the root page
        buffer.unfix(root, owner);
        return res;
    }

    public boolean findOverlapping(final MBR<K> mbr, final Callback<Pair<MBR<K>, V>> callback,
        Owner owner) {
        // Adds a fix to the root page
        RTreePage<K, V> root = tree.getRoot(owner);
        if (root == null)
            return true;
        // Unlog extra root log
        tree.getStatisticsLogger().unlog(Operation.OP_BUFFER_FIX);

        boolean res = root.traverseMDPages(new MBRPredicate<K>(mbr, false, 1),
            new Callback<Page<MBR<K>, V>>() {
                @Override
                public boolean callback(Page<MBR<K>, V> page) {
                    RTreePage<K, V> rPage = (RTreePage<K, V>) page;
                    return rPage.processMBREntries(new Callback<Pair<MBR<K>, PageValue<?>>>() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                            V val = (V) entry.getSecond();
                            if (mbr.overlaps(entry.getFirst())) {
                                if (callback != null) {
                                    boolean continueSearch = callback
                                        .callback(new Pair<MBR<K>, V>(entry.getFirst(), val));
                                    if (!continueSearch)
                                        return false;
                                }
                            }
                            return true;
                        }
                    });
                }
            }, owner);
        // Unfix the extra latch on the root page
        buffer.unfix(root, owner);
        return res;
    }
}
