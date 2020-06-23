package fi.hut.cs.treelib.common;

import java.util.Iterator;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;
import fi.tuska.util.Triple;

/**
 * Interface for manipulating entries in leaf pages (in multiversion
 * databases).
 * 
 * @author thaapasa
 */
public interface LeafEntryMap<K extends Key<K>, V extends PageValue<?>> {

    boolean getRange(KeyRange<K> keyRange, Transaction<K, V> tx, Callback<Pair<K, V>> callback);

    V get(K key, Transaction<K, V> tx);

    int size();

    boolean isEmpty();

    void insert(K key, V value, Transaction<K, V> tx);

    void delete(K key, Transaction<K, V> tx);

    /**
     * @return an iterator over the logical values of the entry map (e.g.,
     * with version ranges)
     */
    Iterator<Triple<MVKeyRange<K>, Boolean, V>> logicalIterator();

    boolean isDirty();

    void clearDirty();

}
