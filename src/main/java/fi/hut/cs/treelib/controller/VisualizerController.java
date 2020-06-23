package fi.hut.cs.treelib.controller;

import java.util.List;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.tuska.util.Pair;

public interface VisualizerController<K extends Key<K>> {
    /**
     * Call from GUI to signal controller to advance in simulation.
     * 
     * @return true if GUI needs to update the tree
     */
    boolean signalAdvance();

    boolean runAll();

    boolean insert(K key, String value);

    String query(K key);

    String query(K key, int version);

    List<Pair<K, String>> rangeQuery(KeyRange<K> range);

    List<Pair<K, String>> rangeQuery(KeyRange<K> range, int version);

    List<String> overlapQuery(K key);

    List<String> exactQuery(K key);

    boolean delete(K key);

    void begin();

    void commit();

    void flush();

    List<String> getOperationLog();

    int getLatestVersion();

    boolean isMultiVersion();

    boolean isActiveTransaction();

    Database<K, ?, ?> getDatabase();

}
