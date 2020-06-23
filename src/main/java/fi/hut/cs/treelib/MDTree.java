package fi.hut.cs.treelib;

import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

/**
 * Models structures that can contain multidimensional data.
 * 
 * @author thaapasa
 */
public interface MDTree<K extends Key<K>, V extends PageValue<?>, P extends Page<MBR<K>, V>>
    extends Tree<MBR<K>, V, P> {

    /**
     * Exact-match query for the given key (MBR).
     * 
     * @param key the key to look for
     */
    boolean getExact(MBR<K> key, Callback<Pair<MBR<K>, V>> callback, Owner owner);

    /**
     * Window-query for the given key (MBR).
     * 
     * Overlapping is defined in MBR.overlaps(MBR).
     * 
     * @param key the key to look for
     * @return the set of objects that overlap with the given MBR.
     */
    boolean getOverlapping(MBR<K> key, Callback<Pair<MBR<K>, V>> callback, Owner owner);

    MBR<K> getExtents();

}
