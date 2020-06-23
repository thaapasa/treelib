package fi.hut.cs.treelib;

import java.util.Iterator;
import java.util.List;

import fi.tuska.util.Pair;

public interface MDPage<K extends Key<K>, V extends PageValue<?>, L extends Key<L>> extends
    Page<MBR<K>, V> {

    MBR<K> getPageMBR();

    List<MBR<K>> getKeys();

    Iterator<Pair<L, MBR<K>>> getUniqueKeys();

    /**
     * Does not work properly for index nodes, if the index node MBR.getMin()
     * is not the same as the coordinate!
     * 
     * @return a fixed child page
     */
    @Override
    MDPage<K, V, L> getChild(MBR<K> key, Owner owner);

    /**
     * @return a fixed child page
     */
    MDPage<K, V, L> getUniqueChild(L key, MBR<K> mbr, Owner owner);

}
