package fi.hut.cs.treelib.btree;

import java.util.Set;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVDatabase;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.AbstractDatabase;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.common.TransactionImpl;
import fi.hut.cs.treelib.concurrency.NoopLatchManager;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;

public class BTreeDatabase<K extends Key<K>, V extends PageValue<?>> extends
    AbstractDatabase<K, V, BTreePage<K, V>> {

    private static final PageID INFO_PAGE_ID = new PageID(1);

    public BTreeDatabase(int bufferSize, SMOPolicy smoPolicy, K keyPrototype, V valuePrototype,
        PageStorage pageStorage) {
        super(bufferSize, pageStorage, smoPolicy, NoopLatchManager.instance(), keyPrototype,
            valuePrototype);

        initStructures();
    }

    @Override
    protected void initStructures() {
        // Get the page buffer
        PageBuffer pageBuffer = getPageBuffer();
        pageBuffer.reservePageID(INFO_PAGE_ID);

        // Create the tree
        BTree<K, V> tree = new BTree<K, V>(INFO_PAGE_ID, this);
        initialize(tree);
    }

    @Override
    protected void clearStructures() {
        // Tree cleared in AbstractDatabase.close()
    }

    /**
     * Default implementation: Do nothing, just return a dummy value.
     */
    @Override
    public int commit(Transaction<K, V> tx) {
        return 0;
    }

    @Override
    public Transaction<K, V> beginReadTransaction(int version) {
        return new TransactionImpl<K, V, BTreePage<K, V>>(this, version, true);
    }

    @Override
    public Transaction<K, V> beginTransaction() {
        return new TransactionImpl<K, V, BTreePage<K, V>>(this, getCommittedVersion(), false);
    }

    @Override
    public Set<Integer> getSeparateRootedVersions() {
        throw new UnsupportedOperationException("There are no multiple versions in B-tree");
    }

    @Override
    public MVDatabase<K, V, BTreePage<K, V>> getMVDatabase() {
        throw new UnsupportedOperationException("B-tree is not a multiversion database");
    }

    @Override
    public boolean isMultiDimension() {
        return false;
    }

    @Override
    public boolean isMultiVersion() {
        return false;
    }

    @Override
    public String toString() {
        return "B-tree database, tree: " + getDatabaseTree();
    }

    @Override
    public BTree<K, V> getDatabaseTree() {
        return (BTree<K, V>) super.getDatabaseTree();
    }

    @Override
    public int getCommittedVersion() {
        // No versions in B-tree
        return 0;
    }
}
