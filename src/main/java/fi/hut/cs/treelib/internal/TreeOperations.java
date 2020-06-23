package fi.hut.cs.treelib.internal;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.PagePath;

public interface TreeOperations<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> {

    /**
     * Finds a path of pages from the given root page to the corresponding
     * leaf page at the given version.
     * 
     * Path will have all pages fixed (once by this operation) to the page
     * buffer. Release after use by calling unfixPath(path).
     */
    void findPathToLeafPage(PageID rootPageID, K key, PagePath<K, V, P> path, int version,
        Owner owner);

    PagePath<K, V, P> validatePathToLeafPage(PageID rootPageID, K key, PagePath<K, V, P> path,
        Transaction<K, V> tx);

    PagePath<K, V, P> findPathForInsert(PageID rootPageID, K key, PagePath<K, V, P> path,
        Transaction<K, V> tx);

    /**
     * Inserts a value with the given key to a page that has been located and
     * is at the end of the page path. Will split the page (and possibly other
     * pages higher in the path) if required.
     * 
     * @param path the path ending at the leaf page where the key-value-pair
     * is to be inserted
     * @return true on success; false on failure. Fails on certain trees if,
     * for example, an attempt is made to insert a duplicate key.
     */
    boolean insert(PagePath<K, V, P> path, K key, V value, Transaction<K, V> tx);

    /**
     * Split the page at the end of the path (it is assumed to be full at this
     * point).
     * 
     * The path is corrected to contain the new page (whose key range contains
     * the given key)
     * 
     * @param checkUnderflowAfterSplit true to check for underflow after split
     * (relevant at least in MVBT). Set to false if the caller will check
     * underflow conditions after the split.
     * @param childID can be null, but when set, the operation path must
     * contain the parent page that contains the child page ID (favored over
     * the key)
     */
    void split(PagePath<K, V, P> path, K key, PageID childID, boolean checkUnderflowAfterSplit,
        Transaction<K, V> tx);

}
