package fi.hut.cs.treelib;

import java.util.List;

import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

/**
 * This interface defines a simple transaction for multidimensional databases.
 * 
 * @author thaapasa
 */
public interface MDTransaction<K extends Key<K>, V extends PageValue<?>> extends
    Transaction<MBR<K>, V> {

    /**
     * @return a single-version tree view of the backing tree. This tree can
     * be used to do modifications to the backing tree.
     */
    // @Override
    // MDTree<K, V, ?> getTransactionTree();

    boolean getExact(MBR<K> key, Callback<Pair<MBR<K>, V>> callback);

    boolean getOverlapping(MBR<K> key, Callback<Pair<MBR<K>, V>> callback);

    /**
     * Exact-match query for the given key (MBR).
     * 
     * @param key the key to look for
     * @return the set of objects that match the given MBR exactly.
     */
    List<V> getExact(MBR<K> key);

    /**
     * Window-query for the given key (MBR).
     * 
     * Overlapping is defined in MBR.overlaps(MBR).
     * 
     * @param key the key to look for
     * @return the set of objects that overlap with the given MBR.
     */
    List<Pair<MBR<K>, V>> getOverlapping(MBR<K> key);

}
