package fi.hut.cs.treelib;

import fi.hut.cs.treelib.common.PagePath;
import fi.tuska.util.Pair;

public interface OrderedTree<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>>
    extends Tree<K, V, P> {

    /**
     * Finds the highest key that is less than or equal to the given key.
     */
    Pair<K, V> floorEntry(K key, Transaction<K, V> tx);

    /**
     * Finds the next key from the given key. Searching for multiple next keys
     * is accelerated by the use of a saved path. On the first call, call with
     * an empty saved path. This method will modify the path to contain the
     * path to the found next key. Release the path after finishing! When no
     * next key can be found anymore, the path is emptied.
     * 
     * <p>
     * Note that the savedPath may not actually represent valid path from root
     * to the leaf node in all situations, if leaf nodes are traversed from
     * left-to-right via sibling links. For example, in B-tree, this saved
     * path will only contain a single leaf page. Therefore this saved path
     * must not be used for other functionality (and must be released before
     * any update operations are performed).
     */
    Pair<K, V> nextEntry(K key, PagePath<K, V, P> savedPath, Transaction<K, V> tx);

}
