package fi.hut.cs.treelib.rtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.BulkLoadable;
import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.MDTransactionImpl;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.concurrency.NoopLatchManager;
import fi.hut.cs.treelib.mdtree.AbstractMDDatabase;
import fi.hut.cs.treelib.rtree.RTreeOperations.SplitType;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.tuska.util.Callback;
import fi.tuska.util.NotImplementedException;
import fi.tuska.util.Pair;

public class RTreeDatabase<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMDDatabase<K, V, MBR<K>, RTreePage<K, V>> implements BulkLoadable<MBR<K>, V> {

    private static final Logger log = Logger.getLogger(RTreeDatabase.class);

    /** Info page has PageID 1. */
    private static final PageID INFO_PAGE_ID = new PageID(1);

    public RTreeDatabase(int bufferSize, SMOPolicy smoPolicy, MBR<K> keyPrototype,
        V valuePrototype, PageStorage storage) {
        super(bufferSize, smoPolicy, storage, NoopLatchManager.instance(), keyPrototype,
            valuePrototype);

        initStructures();
    }

    @Override
    protected void initStructures() {
        SplitType splitType = Configuration.instance().isUseRStarSplit() ? SplitType.R_STAR
            : SplitType.LINEAR_SPLIT;
        RTree<K, V> tree = new RTree<K, V>(splitType, INFO_PAGE_ID, this);
        initialize(tree);
    }

    @Override
    protected void clearStructures() {
        // Tree cleared in AbstractDatabase.close()
    }

    @Override
    public MDTransaction<K, V> beginTransaction() {
        return new MDTransactionImpl<K, V, MBR<K>, RTreePage<K, V>>(this, getCommittedVersion(),
            false);
    }

    @Override
    public Transaction<MBR<K>, V> beginReadTransaction(int version) {
        return new MDTransactionImpl<K, V, MBR<K>, RTreePage<K, V>>(this, version, true);
    }

    @Override
    public RTree<K, V> getDatabaseTree() {
        return (RTree<K, V>) super.getDatabaseTree();
    }

    @Override
    public String toString() {
        return "R-tree db, tree: " + getDatabaseTree();
    }

    @Override
    public void bulkLoad(Iterable<Pair<MBR<K>, V>> keys, Transaction<MBR<K>, V> tx) {
        // R-tree bulk load uses STR algorithm to sort the keys
        // First, sort the keys by the X-dimension, then slice them up, then
        // sort by the Y-dimension
        // 1. Sort by X
        Pair<MBR<K>, V>[] sortedKeys = getSortedArray(keys, SORT_BY_Y);

        // 2. Create slices
        List<Pair<MBR<K>, V>> keyList = Arrays.asList(sortedKeys);
        bulkLoadRoots.clear();
        // No slice boundary checking needed for R-trees, so
        // separatorDimension is -1
        createSTRSlices(keyList, getDatabaseTree().getLeafPageCapacity(), -1,
            getSTRSliceCreator(getDatabaseTree().internalOwner));

        // 3. The new roots have been created. Bulk-load the new roots into a
        // tree structure that is actually inserted into the tree.
        assert !bulkLoadRoots.isEmpty();

        // Check the subtree height (must be same for all the subtrees)
        PageBuffer buffer = getPageBuffer();
        RTreePage<K, V> subTreeRootProto = buffer.fixPage(bulkLoadRoots.get(0), getDatabaseTree()
            .getPageFactory(), false, tx);
        final int subTreeHeight = subTreeRootProto.getHeight();
        buffer.unfix(subTreeRootProto, tx);

        RTreePage<K, V> root = getDatabaseTree().createIndexRoot(subTreeHeight + 1, tx);
        if (bulkLoadRoots.size() > root.getPageEntryCapacity()) {
            throw new NotImplementedException(
                "Bulk-loading is only implemented for a single root for the subtrees");
        }

        for (Iterator<PageID> it = bulkLoadRoots.iterator(); it.hasNext();) {
            PageID rootID = it.next();
            RTreePage<K, V> subRoot = buffer.fixPage(rootID, getDatabaseTree().getPageFactory(),
                false, tx);
            log.debug("");
            if (it.hasNext()) {
                assert subRoot.getHeight() == subTreeHeight : "Sub root height "
                    + subRoot.getHeight() + " != " + subTreeHeight + " (and not last subtree)";
            }
            // Add the subtree root to the main root page
            root.putContents(subRoot.getPageMBR(), subRoot.getPageID());
            buffer.unfix(subRoot, tx);
        }

        buffer.unfix(root, tx);
    }

    private final List<PageID> bulkLoadRoots = new ArrayList<PageID>();

    private final Callback<List<Pair<MBR<K>, V>>> getSTRSliceCreator(final Owner owner) {
        return new Callback<List<Pair<MBR<K>, V>>>() {
            @Override
            public boolean callback(List<Pair<MBR<K>, V>> sublist) {
                // 3. Create a slice from the given sublist
                // Sort the values by Y
                Collections.sort(sublist, SORT_BY_X);

                RTreePage<K, V> root = getDatabaseTree().bulkLoad(sublist, null);
                bulkLoadRoots.add(root.getPageID());
                getPageBuffer().unfix(root, owner);

                return true;
            }
        };
    }

    /**
     * Default implementation: Do nothing, just return a dummy value.
     */
    @Override
    public int commit(Transaction<MBR<K>, V> tx) {
        return 0;
    }

    @Override
    public int getCommittedVersion() {
        // No versions in R-tree
        return 0;
    }

}
