package fi.hut.cs.treelib;

/**
 * Models structures that can contain multiple versions of the same data.
 * 
 * @author thaapasa
 */
public interface MVTree<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> extends
    Tree<K, V, P> {

    /**
     * Returns the root page of the given version of the tree, fixed to the
     * page buffer. Caller must release the page after use!
     * 
     * @return the root page.
     */
    P getRoot(int version, Owner owner);

    PageID getRootPageID(int version);

    /**
     * @return the maximum committed version of the tree.
     */
    int getCommittedVersion();

    int getLatestVersion();

    /**
     * Returns the first accessible version of the tree.
     * 
     * @return the first version of the tree.
     */
    int getFirstVersion();

    /**
     * Returns a (single version) tree representation of the given version of
     * this multi-version tree.
     * 
     * @param version the version to fetch
     * @return a single-version tree at the given version of this tree; or
     * null, if the given version is out of the version range of this tree.
     */
    Tree<K, V, P> getVersionTree(int readVersion);

    @Override
    MVKeyRange<K> getKeyRange();

    /**
     * Returns the page whose key range contains the key in the given version.
     * This page will contain the key if it is in the tree. The page is fixed
     * to the buffer and must be released by the caller.
     * 
     * @param key the key to look for
     * @param version the version to search for
     * @return the page in which the key must be if it is in the tree
     */
    P getPage(K key, int version, Owner owner);

    /**
     * A tree with no root is of height 0. A tree with just the root is of
     * height 1. A tree with a root with some children (but no children's
     * children) is of height 2, and so on.
     * 
     * @return the height of the tree.
     */
    int getHeight(int version);

    @Override
    MVTreeOperations<K, V, P> getOperations();

    /**
     * Counts the number of alive entries in the database.
     * 
     * @return the count of all alive entries in the database
     */
    int countAliveEntries();
}
