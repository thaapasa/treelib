package fi.hut.cs.treelib.btree;

import java.util.Set;
import java.util.TreeSet;

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

public class MVVBTDatabase<K extends Key<K>, V extends PageValue<?>> extends
    AbstractDatabase<K, V, BTreePage<K, V>> implements MVDatabase<K, V, BTreePage<K, V>> {

    private static final PageID INFO_PAGE_ID = new PageID(1);

    public MVVBTDatabase(int bufferSize, SMOPolicy smoPolicy, K keyPrototype, V valuePrototype,
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
        MVVBTree<K, V> tree = new MVVBTree<K, V>(INFO_PAGE_ID, this);
        initialize(tree);
    }

    @Override
    protected void clearStructures() {
        // Tree cleared in AbstractDatabase.close()
    }

    @Override
    public int commit(Transaction<K, V> tx) {
        if (tx.isReadOnly())
            return tx.getReadVersion();
        else
            return getDatabaseTree().commitActiveTransaction(tx);
    }

    @Override
    public Transaction<K, V> beginReadTransaction(int version) {
        return new TransactionImpl<K, V, BTreePage<K, V>>(this, version, true);
    }

    @Override
    public Transaction<K, V> beginTransaction() {
        return new TransactionImpl<K, V, BTreePage<K, V>>(this, getDatabaseTree()
            .beginNewTransaction(), false);
    }

    @Override
    public Set<Integer> getSeparateRootedVersions() {
        Set<Integer> set = new TreeSet<Integer>();
        set.add(0);
        return set;
    }

    @Override
    public MVDatabase<K, V, BTreePage<K, V>> getMVDatabase() {
        return this;
    }

    @Override
    public boolean isMultiDimension() {
        return false;
    }

    @Override
    public boolean isMultiVersion() {
        return true;
    }

    @Override
    public String toString() {
        return "MV-VBT database, tree: " + getDatabaseTree();
    }

    @Override
    public MVVBTree<K, V> getDatabaseTree() {
        return (MVVBTree<K, V>) super.getDatabaseTree();
    }

    @Override
    public int getCommittedVersion() {
        return getDatabaseTree().getCommittedVersion();
    }

}
