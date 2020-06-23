package fi.hut.cs.treelib;

import fi.tuska.util.Pair;

/**
 * This interface defines a simple transaction for multidimensional databases.
 * 
 * @author thaapasa
 */
public interface OrderedTransaction<K extends Key<K>, V extends PageValue<?>> extends
    Transaction<K, V> {

    /**
     * Finds the highest key that is less than or equal to the given key.
     */
    Pair<K, V> floorEntry(K key);

    /**
     * Finds the next key from the given key.
     */
    Pair<K, V> nextEntry(K key);

}
