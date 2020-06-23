package fi.hut.cs.treelib;

import fi.tuska.util.Pair;

public interface BulkLoadable<K extends Key<K>, V extends PageValue<?>> {

    /**
     * Generally only allowed on an empty database.
     */
    void bulkLoad(Iterable<Pair<K, V>> keys, Transaction<K, V> tx);

}
