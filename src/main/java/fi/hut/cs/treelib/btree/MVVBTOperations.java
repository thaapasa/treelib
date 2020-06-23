package fi.hut.cs.treelib.btree;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVTreeOperations;
import fi.hut.cs.treelib.PageValue;

/**
 * This class is not actually used, it's just there so that the MVVBTree class
 * interface has a correct return type for getOperations()
 * 
 * @author thaapasa
 */
public class MVVBTOperations<K extends Key<K>, V extends PageValue<?>> extends
    BTreeOperations<K, V> implements MVTreeOperations<K, V, BTreePage<K, V>> {

    protected MVVBTOperations(BTree<K, V> tree) {
        super(tree);
    }

}
