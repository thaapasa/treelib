package fi.hut.cs.treelib.common;

import java.io.IOException;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.concurrency.LatchManager;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.NotImplementedException;

public abstract class AbstractDatabase<K extends Key<K>, V extends PageValue<?>, P extends AbstractTreePage<K, V, P>>
    extends DatabaseConfigurationImpl<K, V> implements Database<K, V, P>, Component {

    private static final Logger log = Logger.getLogger(AbstractDatabase.class);
    protected AbstractTree<K, V, P> tree;

    protected AbstractDatabase(int bufferSize, PageStorage pageStorage, SMOPolicy smoPolicy,
        LatchManager latchManager, K keyPrototype, V valuePrototype) {
        super(keyPrototype, valuePrototype, pageStorage.getPageSize(), bufferSize, smoPolicy,
            pageStorage, latchManager);
    }

    protected void initialize(AbstractTree<K, V, P> tree) {
        this.tree = tree;
    }

    @Override
    public final String getIdentifier() {
        return tree.getIdentifier();
    }

    @Override
    public void flush() {
        tree.flush();
    }

    @Override
    public void reinit() {
        flush();
    }

    @Override
    public void close() {
        flush();
        try {
            // Closing the page buffer closes the page storage also
            pageBuffer.close();
        } catch (IOException e) {
            log.error("Error when closing database: " + e, e);
        }
        tree.close();
        tree = null;
        clearStructures();
        pageBuffer.clearStructures();
    }

    protected abstract void clearStructures();

    @Override
    public void reopen() {
        if (!pageBuffer.isInitialized()) {
            pageBuffer.initialize();
        }
        initStructures();
        reinit();
    }

    protected abstract void initStructures();

    @Override
    public void setStatisticsLogger(StatisticsLogger stats) {
        tree.setStatisticsLogger(stats);
    }

    @Override
    public StatisticsLogger getStatisticsLogger() {
        return tree.getStatisticsLogger();
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
        tree.printDebugInfo();

        System.out.println("Page buffer:");
        pageBuffer.printDebugInfo();
    }

    @Override
    public void abort(Transaction<K, V> tx) {
        if (tx.isReadOnly()) {
            // Read-only transactions require no special actions
            return;
        }
        throw new NotImplementedException("Transaction abort not implemented");
    }

    @Override
    public void traversePages(Predicate<KeyRange<K>> predicate, Callback<Page<K, V>> operation,
        Owner owner) {
        getDatabaseTree().traversePages(predicate, operation, owner);
    }

    @Override
    public AbstractTree<K, V, P> getDatabaseTree() {
        return tree;
    }

    @Override
    public void checkConsistency(Object... params) {
        tree.checkConsistency(params);
        pageBuffer.checkConsistency();
    }

    @Override
    public void requestMaintenance() {
        // Default: No maintenance operations
    }

    @Override
    public int getLatestVersion() {
        return getCommittedVersion();
    }

    @Override
    public boolean isEmpty(Transaction<K, V> tx) {
        return getDatabaseTree().isEmpty(tx);
    }

}
