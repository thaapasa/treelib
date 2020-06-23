package fi.hut.cs.treelib.mdtree;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.BulkLoadable;
import fi.hut.cs.treelib.Coordinate;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.DatabaseConfigurationImpl;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.MDTransactionImpl;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.common.TreeShortcuts;
import fi.hut.cs.treelib.concurrency.NoopLatchManager;
import fi.hut.cs.treelib.mdtree.OMDTreeOperations.SplitType;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.tuska.util.Callback;
import fi.tuska.util.Converter;
import fi.tuska.util.Pair;

/**
 * Packed J-tree database consists of a (root) J-tree that holds PageIDs of
 * separate J-tree slices. Must be created by bulk-loading the database.
 * 
 * @author thaapasa
 */
public class PackedJTreeDatabase<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMDDatabase<K, V, Coordinate<K>, OMDPage<K, V, Coordinate<K>>> implements
    BulkLoadable<MBR<K>, V> {

    private static final Logger log = Logger.getLogger(PackedJTreeDatabase.class);

    /** Info page has PageID 1. */
    private static final PageID ROOT_PAGE_ID = new PageID(1);

    private static final SplitType SPLIT_TYPE = SplitType.LINEAR_SPLIT;

    private JTree<K, IntegerValue> rootTree;

    public PackedJTreeDatabase(int bufferSize, int pageSize, SMOPolicy smoPolicy,
        final MBR<K> keyPrototype, V valuePrototype, PageStorage storage) {
        super(bufferSize, smoPolicy, storage, NoopLatchManager.instance(), keyPrototype,
            valuePrototype);

        initStructures();
    }

    @Override
    protected void initStructures() {
        PageBuffer buffer = getPageBuffer();
        buffer.reservePageID(ROOT_PAGE_ID);

        // Override search key converter definition to switch the key ordering
        // for the root tree
        this.rootTree = new JTree<K, IntegerValue>(SPLIT_TYPE, ROOT_PAGE_ID,
            new Converter<MBR<K>, Coordinate<K>>() {
                private final Coordinate<K> maxKey = keyPrototype.getMax().getMaxKey();

                @Override
                @SuppressWarnings("unchecked")
                public Coordinate<K> convert(MBR<K> mbr) {
                    if (mbr == null) {
                        return maxKey;
                    }
                    K y = mbr.getMin().get(1);
                    return new Coordinate<K>(y, y.getMinKey());
                }
            }, new DatabaseConfigurationImpl<MBR<K>, IntegerValue>(this, IntegerValue.PROTOTYPE,
                getSMOPolicy()));

        PackedJTree<K, V> treeWrapper = new PackedJTree<K, V>(rootTree, SPLIT_TYPE, this);
        // Initialize with a dummy tree
        initialize(treeWrapper);
    }

    @Override
    protected void clearStructures() {
        rootTree.close();
        rootTree = null;
    }

    @Override
    public void setStatisticsLogger(StatisticsLogger stats) {
        super.setStatisticsLogger(stats);
        rootTree.setStatisticsLogger(stats);
    }

    @Override
    public MDTransaction<K, V> beginTransaction() {
        return new MDTransactionImpl<K, V, Coordinate<K>, OMDPage<K, V, Coordinate<K>>>(this,
            getCommittedVersion(), false);
    }

    @Override
    public Transaction<MBR<K>, V> beginReadTransaction(int version) {
        return new MDTransactionImpl<K, V, Coordinate<K>, OMDPage<K, V, Coordinate<K>>>(this,
            version, true);
    }

    @Override
    public PackedJTree<K, V> getDatabaseTree() {
        return (PackedJTree<K, V>) super.getDatabaseTree();
    }

    @Override
    public void bulkLoad(Iterable<Pair<MBR<K>, V>> keys, Transaction<MBR<K>, V> tx) {
        // Sort keys by minimum Y coordinate
        Pair<MBR<K>, V>[] keyArray = getSortedArray(keys, SORT_BY_Y);
        final int keyCount = keyArray.length;
        log.info("Bulk-loading " + keyCount + " keys to Packed J-tree");

        List<Pair<MBR<K>, V>> keyList = Arrays.asList(keyArray);

        createSTRSlices(keyList, rootTree.getLeafPageCapacity(), 1, getSTRSliceCreator(tx));
    }

    /**
     * Called by the createSTRSlices-function. Creates a single slice when
     * bulk-loading.
     */
    private Callback<List<Pair<MBR<K>, V>>> getSTRSliceCreator(final Transaction<MBR<K>, V> tx) {
        return new Callback<List<Pair<MBR<K>, V>>>() {
            @Override
            public boolean callback(List<Pair<MBR<K>, V>> sublist) {
                PageBuffer buffer = getPageBuffer();
                // Create root page for the new tree
                PageID newRootID = buffer.reserveNewPageID(tx);

                // Create the new tree
                JTree<K, V> newTree = new JTree<K, V>(SPLIT_TYPE, newRootID,
                    PackedJTreeDatabase.this);
                newTree.setStatisticsLogger(getDatabaseTree().getStatisticsLogger());

                // Sort the sub-list by the natural ordering of the MBRs
                Collections.sort(sublist);
                // Bulk-load the values into the new tree
                OMDPage<K, V, Coordinate<K>> newRoot = newTree.bulkLoad(sublist, null);
                assert newRoot.getPageID().equals(newRootID) : newRoot.getPageID() + " != "
                    + newRootID;
                // Attach the new root to the tree
                newTree.attachRoot(newRoot, tx);
                buffer.unfix(newRoot, tx);

                // Subtree created
                log.info("Created a subtree of height " + newTree.getHeight() + "; subtree is "
                    + newTree);

                // Insert pointer to the new J-tree to the root tree
                MBR<K> extents = newTree.getExtents();
                newTree.close();
                if (!extents.isPrototype()) {
                    TreeShortcuts.insert(rootTree, extents, new IntegerValue(newRootID),
                        getDatabaseTree().rootsTX);
                } else {
                    log.warn("Slice generation failed, tree extents have not been updated: "
                        + newTree);
                }
                return true;
            }
        };
    }

    @Override
    public String toString() {
        return "Packed J-tree database, roots: " + rootTree;
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
        // No versions in packed J-tree
        return 0;
    }

}
