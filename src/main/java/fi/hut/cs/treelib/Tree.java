package fi.hut.cs.treelib;

import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.internal.TreeOperations;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public interface Tree<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> {

    /**
     * Returns the root page of the tree, fixed to the page buffer. Caller
     * must release the page after use!
     * 
     * @return the root page.
     */
    P getRoot(Owner owner);

    PageID getRootPageID();

    /**
     * @return true if there are no keys or values in the tree; false
     * otherwise.
     */
    boolean isEmpty(Transaction<K, V> tx);

    /**
     * A tree with no root is of height 0. A tree with just the root is of
     * height 1. A tree with a root with some children (but no children's
     * children) is of height 2, and so on.
     * 
     * @return the height of the tree.
     */
    int getHeight();

    /**
     * Inserts a value with the given key into the tree.
     * 
     * @param key the key
     * @param value the value
     * @return true if the key could be inserted; false otherwise
     */
    boolean insert(K key, V value, PagePath<K, V, P> savedPath, Transaction<K, V> tx);

    /**
     * Deletes the given key from the tree, re-using the page path that is
     * given.
     * 
     * @param key the key to delete
     */
    boolean delete(K key, PagePath<K, V, P> savedPath, Transaction<K, V> tx);

    /**
     * Finds the value with the given key from the tree.
     * 
     * @param key the key to look for
     * @return the value attached to the key; or null, if no such value was
     * found
     */
    V get(K key, Transaction<K, V> tx);

    /**
     * Checks if the tree contains the given key.
     * 
     * @param key the key to look for
     * @return true if the key is found in the tree; false otherwise
     */
    boolean contains(K key, Transaction<K, V> tx);

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
    boolean getRange(KeyRange<K> range, Callback<Pair<K, V>> callback, Transaction<K, V> tx);

    /**
     * @return true if the keys in the tree are ordered.
     */
    boolean isOrdered();

    boolean isMultiVersion();

    /**
     * @return the range of keys inside this tree
     */
    KeyRange<K> getKeyRange();

    /**
     * Relevant for database structures that are not really trees, such as
     * MVBT. In these, this represents the height of the highest version tree.
     * 
     * @return the maximum height of this graph
     */
    int getMaxHeight();

    /**
     * Returns the leaf page whose key range contains the key. This page will
     * contain the key if it is in the tree. The returned page will be fixed
     * to page buffer and must be unfixed by the caller.
     * 
     * @param key the key to look for
     * @return the page in which the key must be if it is in the tree; the
     * page is fixed to page buffer
     */
    P getPage(K key, Owner owner);

    /**
     * Forces the tree contents into the underlying storage.
     */
    void flush();

    /**
     * @return the page buffer of the tree
     */
    PageBuffer getPageBuffer();

    /**
     * @return the page factory used to create new pages in this tree
     */
    PageFactory<P> getPageFactory();

    TreeOperations<K, V, P> getOperations();

    K getKeyPrototype();

    V getValuePrototype();

    void setStatisticsLogger(StatisticsLogger stats);

    StatisticsLogger getStatisticsLogger();

    /**
     * Counts all entries in the database. For multiversion trees, counts the
     * number of all individual leaf page entries (that is, different versions
     * of the same key are added up).
     * 
     * @return the count of all individual leaf page entries in the database
     */
    int countAllEntries();

    /**
     * @return an informative name for printing, such as "B-tree"
     */
    String getName();

    /**
     * @return an identifier, such as "btree"
     */
    String getIdentifier();

    void close();

}
