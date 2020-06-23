package fi.hut.cs.treelib.common;

import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Action;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class TransactionImpl<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>>
    implements Transaction<K, V> {

    private static final Logger log = Logger.getLogger(TransactionImpl.class);

    /** Unique transaction identifier and also owner ID. */
    private final int transactionID;
    private final int readVersionID;
    private int commitVer;
    private final boolean readOnly;
    protected final Database<K, V, P> database;
    protected boolean committed;

    public TransactionImpl(Database<K, V, P> database, int readVersionID, boolean readOnly) {
        this.transactionID = OwnerImpl.createID();
        this.readOnly = readOnly;
        this.database = database;
        this.readVersionID = readVersionID;
        this.committed = false;
        this.commitVer = -1;
    }

    public TransactionImpl(Database<K, V, P> database, int readVersionID, Owner owner,
        boolean readOnly) {
        this.transactionID = owner.getOwnerID();
        this.readOnly = readOnly;
        this.database = database;
        this.readVersionID = readVersionID;
        this.committed = false;
        this.commitVer = -1;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public boolean isUpdating() {
        return !isReadOnly();
    }

    public void abort() {
        if (committed)
            throw new IllegalStateException("Already committed");

        log.debug("Aborting transaction " + transactionID);
        committed = true;
        database.abort(this);
    }

    @Override
    public void commit() {
        if (committed)
            throw new IllegalStateException("Already committed");

        log.debug("Committing transaction " + transactionID);
        committed = true;
        commitVer = database.commit(this);
    }

    /**
     * Default implementation: returns the version given in the constructor.
     */
    @Override
    public int getTransactionID() {
        return transactionID;
    }

    /**
     * Default implementation: returns the version given in the constructor.
     */
    @Override
    public int getReadVersion() {
        return readVersionID;
    }

    /**
     * Default implementation: returns the version given in the constructor.
     */
    @Override
    public int getCommitVersion() {
        if (readOnly)
            throw new UnsupportedOperationException(
                "Commit-time ID not defined for read-only transactions");
        if (!committed)
            throw new IllegalStateException("Transaction not yet committed");
        return commitVer;
    }

    protected void checkNotReadOnly() {
        if (readOnly)
            throw new UnsupportedOperationException("This transaction is read-only!");
    }

    @Override
    public boolean contains(K key) {
        if (committed)
            throw new IllegalStateException("Already committed");

        if (log.isDebugEnabled()) {
            log.debug(database.getDatabaseTree().getName() + " action: contains " + key);
        }
        database.getStatisticsLogger().newAction(Action.ACTION_CONTAINS);

        boolean result = database.getDatabaseTree().contains(key, this);
        runAfterAction();
        if (result) {
            database.getStatisticsLogger().log(Operation.OP_QUERY_FOUND_OBJECT);
        }
        return result;
    }

    @Override
    public V get(K key) {
        if (committed)
            throw new IllegalStateException("Already committed");

        if (log.isDebugEnabled()) {
            log.debug(database.getDatabaseTree().getName() + " action: query " + key);
        }
        database.getStatisticsLogger().newAction(Action.ACTION_QUERY);

        V val = database.getDatabaseTree().get(key, this);
        if (val != null) {
            database.getStatisticsLogger().log(Operation.OP_QUERY_FOUND_OBJECT);
        }
        runAfterAction();
        return val;
    }

    @Override
    public boolean getAll(final Callback<Pair<K, V>> callback) {
        return getRange(database.getKeyPrototype().getEntireRange(), callback);
    }

    @Override
    public List<Pair<K, V>> getAll() {
        return getRange(database.getKeyPrototype().getEntireRange());
    }

    @Override
    public List<Pair<K, V>> getRange(final KeyRange<K> range) {
        if (committed)
            throw new IllegalStateException("Already committed");

        if (log.isDebugEnabled()) {
            log.debug(database.getDatabaseTree().getName() + " action: range query " + range);
        }
        database.getStatisticsLogger().newAction(Action.ACTION_RANGE_QUERY);

        ListCreatingCallback<Pair<K, V>> lc = new ListCreatingCallback<Pair<K, V>>(database
            .getStatisticsLogger());
        database.getDatabaseTree().getRange(range, lc, this);
        runAfterAction();
        return lc.getList();
    }

    @Override
    public boolean getRange(KeyRange<K> range, final Callback<Pair<K, V>> callback) {
        if (committed)
            throw new IllegalStateException("Already committed");
        final StatisticsLogger stats = database.getStatisticsLogger();

        if (log.isDebugEnabled()) {
            log.debug(database.getDatabaseTree().getName() + " action: range query " + range);
        }
        stats.newAction(Action.ACTION_RANGE_QUERY);

        boolean res = database.getDatabaseTree().getRange(range, new Callback<Pair<K, V>>() {
            @Override
            public boolean callback(Pair<K, V> object) {
                stats.log(Operation.OP_QUERY_FOUND_OBJECT);
                return callback != null ? callback.callback(object) : true;
            }
        }, this);

        runAfterAction();
        return res;
    }

    @Override
    public boolean delete(K key) {
        checkNotReadOnly();
        if (committed)
            throw new IllegalStateException("Already committed");

        if (log.isDebugEnabled()) {
            log.debug(database.getDatabaseTree().getName() + " action: delete " + key);
        }
        database.getStatisticsLogger().newAction(Action.ACTION_DELETE);

        PagePath<K, V, P> path = new PagePath<K, V, P>(true);
        boolean result = database.getDatabaseTree().delete(key, path, this);
        database.getPageBuffer().unfix(path, this);
        if (result) {
            database.getStatisticsLogger().log(Operation.OP_OBJECT_DELETED);
        }
        runAfterAction();
        return result;
    }

    @Override
    public boolean insert(K key, V value) {
        checkNotReadOnly();
        if (committed)
            throw new IllegalStateException("Already committed");

        if (log.isDebugEnabled()) {
            log.debug(database.getDatabaseTree().getName() + " action: insert " + key);
        }
        database.getStatisticsLogger().newAction(Action.ACTION_INSERT);

        PagePath<K, V, P> path = new PagePath<K, V, P>(true);
        boolean result = database.getDatabaseTree().insert(key, value, path, this);
        database.getPageBuffer().unfix(path, this);
        if (result) {
            database.getStatisticsLogger().log(Operation.OP_OBJECT_INSERTED);
        }
        runAfterAction();
        return result;
    }

    protected void runAfterAction() {
        if (database.getDatabaseTree() instanceof AbstractTree<?, ?, ?>) {
            ((AbstractTree<K, V, ?>) database.getDatabaseTree()).runAfterAction();
        }
    }

    @Override
    public String toString() {
        return String.format("%s: %s TX %d reading %d", getDebugID(), readOnly ? "Read-only"
            : "Updating", transactionID, readVersionID);
    }

    @Override
    public int getOwnerID() {
        return transactionID;
    }

    @Override
    public String getName() {
        return "transaction-" + transactionID;
    }

    @Override
    public String getDebugID() {
        if (committed)
            return String.format("%sTX:%d:C:%d:R:%d", readOnly ? "r" : "u", transactionID,
                commitVer, readVersionID);
        else
            return String.format("%sTX:%d:R:%d", readOnly ? "r" : "u", transactionID,
                readVersionID);
    }

}
