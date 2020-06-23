package fi.hut.cs.treelib.mdtree;

import fi.hut.cs.treelib.Coordinate;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.MDTransactionImpl;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.mdtree.OMDTreeOperations.SplitType;
import fi.hut.cs.treelib.storage.PageStorage;

public class JTreeDatabase<K extends Key<K>, V extends PageValue<?>> extends
    OMDDatabase<K, V, Coordinate<K>, OMDPage<K, V, Coordinate<K>>> {

    /** Root page of OMD trees has PageID 1. */
    private static final PageID ROOT_PAGE_ID = new PageID(1);

    public JTreeDatabase(int bufferSize, SMOPolicy smoPolicy, MBR<K> keyPrototype,
        V valuePrototype, PageStorage storage) {
        super(bufferSize, smoPolicy, storage, keyPrototype, valuePrototype);

        initStructures();
    }

    @Override
    protected void initStructures() {
        getPageBuffer().reservePageID(ROOT_PAGE_ID);
        JTree<K, V> tree = new JTree<K, V>(SplitType.LINEAR_SPLIT, ROOT_PAGE_ID, this);
        initialize(tree);
    }

    @Override
    protected void clearStructures() {
        // Tree cleared in AbstractDatabase.close()
    }

    @Override
    public MDTransaction<K, V> beginTransaction() {
        return new MDTransactionImpl<K, V, Coordinate<K>, OMDPage<K, V, Coordinate<K>>>(this,
            getCommittedVersion(), false);
    }

    @Override
    public MDTransaction<K, V> beginReadTransaction(int version) {
        return new MDTransactionImpl<K, V, Coordinate<K>, OMDPage<K, V, Coordinate<K>>>(this,
            version, true);
    }

    @Override
    public String toString() {
        return "J-tree db, tree: " + getDatabaseTree();
    }

    @Override
    public JTree<K, V> getDatabaseTree() {
        return (JTree<K, V>) super.getDatabaseTree();
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
        // No versions in J-tree
        return 0;
    }

}
