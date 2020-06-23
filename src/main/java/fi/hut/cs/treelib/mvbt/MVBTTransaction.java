package fi.hut.cs.treelib.mvbt;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.TransactionImpl;

public class MVBTTransaction<K extends Key<K>, V extends PageValue<?>> extends
    TransactionImpl<K, V, MVBTPage<K, V>> implements Transaction<K, V> {

    protected final MVBTDatabase<K, V> database;

    public MVBTTransaction(MVBTDatabase<K, V> database, int readVersion, boolean readOnly) {
        super(database, readVersion, readOnly);
        this.database = database;
    }

    @Override
    public void abort() {
        throw new UnsupportedOperationException("abort() not supported");
    }

    @Override
    public int getTransactionID() {
        throw new UnsupportedOperationException();
    }

    /**
     * MVBT transaction is not a proper transaction, as MVBT only supports
     * single update/transaction, so circumvent the problem by changing the
     * active transaction's ID.
     */
    @Override
    public int getReadVersion() {
        return isReadOnly() ? super.getReadVersion() : database.getActiveVersion();
    }

}
