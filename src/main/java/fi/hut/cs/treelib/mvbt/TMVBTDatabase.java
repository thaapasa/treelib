package fi.hut.cs.treelib.mvbt;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.common.TransactionImpl;
import fi.hut.cs.treelib.storage.PageStorage;

public class TMVBTDatabase<K extends Key<K>, V extends PageValue<?>> extends MVBTDatabase<K, V>
    implements Component {

    private Transaction<K, V> activeTransaction;

    public TMVBTDatabase(int bufferSize, SMOPolicy smoPolicy, K keyPrototype, V valuePrototype,
        PageStorage pageStorage) {
        super(bufferSize, smoPolicy, keyPrototype, valuePrototype, pageStorage);
    }

    @Override
    protected TMVBTree<K, V> createBackingTree() {
        BTree<IntegerKey, PageID> rootTree = createRootTree();

        TMVBTree<K, V> tree = new TMVBTree<K, V>(INFO_PAGE_ID, rootTree, this);
        return tree;
    }

    @Override
    public Transaction<K, V> beginTransaction() {
        if (activeTransaction != null)
            throw new IllegalStateException(
                "TMVBT cannot have more than one transaction running at the same time!");
        Owner owner = new OwnerImpl("TMVBT-tx");
        int txReadVer = getDatabaseTree().beginTransaction(owner);
        activeTransaction = new TransactionImpl<K, V, MVBTPage<K, V>>(this, txReadVer, owner,
            false);
        return activeTransaction;
    }

    @Override
    public Transaction<K, V> beginReadTransaction(int version) {
        return new TransactionImpl<K, V, MVBTPage<K, V>>(this, version, true);
    }

    @Override
    public int commit(Transaction<K, V> tx) {
        if (tx.isReadOnly())
            return tx.getReadVersion();

        if (activeTransaction == null)
            throw new IllegalStateException("No active transaction!");
        if (activeTransaction != tx)
            throw new IllegalArgumentException(
                "Trying to commit another transaction than the active transaction");

        // Do the commit
        int cv = getDatabaseTree().commitTransaction(tx);
        activeTransaction = null;
        return cv;
    }

    @Override
    public int getCommittedVersion() {
        return getDatabaseTree().getCommittedVersion();
    }

    @Override
    public String toString() {
        return "TMVBT database";
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
        System.out.println("Backing tree: " + getDatabaseTree());
        getDatabaseTree().printDebugInfo();
    }

    @Override
    public TMVBTree<K, V> getDatabaseTree() {
        return (TMVBTree<K, V>) super.getDatabaseTree();
    }

}
