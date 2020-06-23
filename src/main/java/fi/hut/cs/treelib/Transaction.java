package fi.hut.cs.treelib;

import java.util.List;

import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

/**
 * This interface defines a simple transaction.
 * 
 * @author thaapasa
 */
public interface Transaction<K extends Key<K>, V extends PageValue<?>> extends Owner {

    boolean isReadOnly();

    boolean isUpdating();

    /**
     * Returns a unique ID that identifies this transaction. Guaranteed to be
     * unique between all transaction objects (might be shared by commit
     * transaction IDs, though).
     * 
     * <p>
     * For CMVBT, this returns the start-time version of the transaction.
     * 
     * @return a unique identifier identifying this transaction
     */
    int getTransactionID();

    /**
     * Returns the commit-time version number that this transaction is
     * reading. For non-concurrent implementations, this is the same as
     * getTransactionID().
     * 
     * <p>
     * For CMVBT, this returns the maximum commit-time version that existed in
     * the database at the beginning of the transaction.
     * 
     * @return the commit-time version number this transaction is reading from
     * the database
     */
    int getReadVersion();

    /**
     * Returns the commit-time version number of this transaction. For
     * non-concurrent implementations, this is the same as getTransactionID().
     * 
     * <p>
     * For CMVBT, this returns the commit-time transaction ID, and therefore
     * you cannot call this method before the transaction has committed
     * (because the ID is not known before that).
     * 
     * @return the commit-time version number of this transaction
     */
    int getCommitVersion();

    /**
     * Inserts a value with the given key into the tree.
     * 
     * @param key the key
     * @param value the value
     * @return true if the key could be inserted; false otherwise
     */
    boolean insert(K key, V value);

    /**
     * Deletes the given key from the tree.
     * 
     * @param key the key to delete
     * @return the value that was attached to the given key; or null, if no
     * such key was found.
     */
    boolean delete(K key);

    /**
     * Finds the value with the given key from the tree.
     * 
     * @param key the key to look for
     * @return the value attached to the key; or null, if no such value was
     * found
     */
    V get(K key);

    /**
     * Finds all value in the given key range from the tree.
     * 
     * @param range the key range to look for
     * @return the list of matching key-value pairs
     */
    List<Pair<K, V>> getRange(KeyRange<K> range);

    /**
     * Callback to traverse the range of entries in this transaction. The
     * given callback must return true if the search is to be continued; and
     * false to stop the search.
     * 
     * @param range the key range
     * @param callback the callback to call for each entry
     * @return true if all values were browsed (all callbacks returned true),
     * and false if a callback returned false to indicated search stopping
     */
    boolean getRange(KeyRange<K> range, Callback<Pair<K, V>> callback);

    /**
     * Callback to traverse all the contents of the database in this
     * transaction.
     * 
     * @param callback the callback to call for each entry
     * @return true if all values were browsed (all callbacks returned true),
     * and false if a callback returned false to indicated search stopping
     */
    boolean getAll(Callback<Pair<K, V>> callback);

    /**
     * Convenience method for retrieving all value in the given key range from
     * the tree in a list.
     * 
     * @return the list of matching key-value pairs
     */
    List<Pair<K, V>> getAll();

    /**
     * Checks if the tree contains the given key.
     * 
     * @param key the key to look for
     * @return true if the key is found in the tree; false otherwise
     */
    boolean contains(K key);

    /**
     * Commits the transaction.
     */
    void commit();

    /**
     * Aborts the transaction (roll-back).
     */
    void abort();

    String getDebugID();

}
