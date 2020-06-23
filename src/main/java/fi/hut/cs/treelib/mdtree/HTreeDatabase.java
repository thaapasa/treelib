package fi.hut.cs.treelib.mdtree;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.LongKey;
import fi.hut.cs.treelib.common.MDTransactionImpl;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.storage.PageStorage;

public class HTreeDatabase<K extends Key<K>, V extends PageValue<?>> extends
    OMDDatabase<K, V, LongKey, OMDPage<K, V, LongKey>> {

    /** Root page of OMD trees has PageID 1. */
    private static final PageID ROOT_PAGE_ID = new PageID(1);

    public HTreeDatabase(int bufferSize, SMOPolicy smoPolicy, MBR<K> keyPrototype,
        V valuePrototype, PageStorage storage) {
        super(bufferSize, smoPolicy, storage, keyPrototype, valuePrototype);

        initStructures();
    }

    @Override
    protected void initStructures() {
        getPageBuffer().reservePageID(ROOT_PAGE_ID);
        HTree<K, V> tree = new HTree<K, V>(ROOT_PAGE_ID, this);
        initialize(tree);
    }

    @Override
    protected void clearStructures() {
        // Tree cleared in AbstractDatabase.close()
    }

    @Override
    public MDTransaction<K, V> beginTransaction() {
        return new MDTransactionImpl<K, V, LongKey, OMDPage<K, V, LongKey>>(this,
            getCommittedVersion(), false);
    }

    @Override
    public MDTransaction<K, V> beginReadTransaction(int version) {
        return new MDTransactionImpl<K, V, LongKey, OMDPage<K, V, LongKey>>(this, version, true);
    }

    @Override
    public HTree<K, V> getDatabaseTree() {
        return (HTree<K, V>) super.getDatabaseTree();
    }

    @Override
    public String toString() {
        return "Hilbert R-tree db, tree: " + getDatabaseTree();
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
        // No versions in H-tree
        return 0;
    }

}
