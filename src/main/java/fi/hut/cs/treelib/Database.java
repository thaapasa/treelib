package fi.hut.cs.treelib;

import java.util.Set;

import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;

/**
 * Main, top-most interface of the databases.
 * 
 * @author thaapasa
 */
public interface Database<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> extends
    Component {

    /**
     * Transactions are used to interact with the database.
     * 
     * @return a new transaction on the database
     */
    Transaction<K, V> beginTransaction();

    /**
     * Transactions are used to interact with the database.
     * 
     * @return a new transaction on the database
     */
    Transaction<K, V> beginReadTransaction(int version);

    /**
     * Commits the given transaction. Called from the transaction itself.
     * 
     * @param tx the transaction
     * @return the commit-time version number of the transaction
     */
    int commit(Transaction<K, V> tx);

    /**
     * Aborts the given transaction. Called from the transaction itself.
     * 
     * @param tx the transaction
     */
    void abort(Transaction<K, V> tx);

    /**
     * @return the backing tree, for example for visualization
     */
    Tree<K, V, P> getDatabaseTree();

    boolean isMultiVersion();

    MVDatabase<K, V, P> getMVDatabase();

    boolean isMultiDimension();

    /**
     * @return the latest committed version
     */
    int getCommittedVersion();

    /**
     * @return the latest version in the database - most often the committed
     * version, except for MVBT databases
     */
    int getLatestVersion();

    /**
     * @return the set of versions for which the roots of the database graph
     * differ. Used for visualization, for example.
     */
    Set<Integer> getSeparateRootedVersions();

    /**
     * Forces the contents of the database to disk.
     */
    void flush();

    void setStatisticsLogger(StatisticsLogger stats);

    StatisticsLogger getStatisticsLogger();

    void traversePages(Predicate<KeyRange<K>> predicate, Callback<Page<K, V>> operation,
        Owner owner);

    String getIdentifier();

    PageStorage getPageStorage();

    PageBuffer getPageBuffer();

    K getKeyPrototype();

    V getValuePrototype();

    int getPageSize();

    int getBufferSize();

    /**
     * Runs maintenance operations on the database, if necessary.
     */
    void requestMaintenance();

    /**
     * Call to close the database. Releases the file handle (if any),
     * detaching the database from the filesystem.
     * 
     * <p>
     * To externally modify the database files, you can call close(), do
     * modifications, and then call reopen() to reattach.
     */
    void close();

    /**
     * Reattaches the database to the filesystem. Call if you have closed the
     * filesystem handle with the close() method.
     */
    void reopen();

    /**
     * Applies changes to settings, flushes the database. Can be used in
     * between successive test runs.
     */
    void reinit();

    boolean isEmpty(Transaction<K, V> tx);

}
