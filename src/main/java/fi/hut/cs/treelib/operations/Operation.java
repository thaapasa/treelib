package fi.hut.cs.treelib.operations;

import fi.hut.cs.treelib.Key;

public interface Operation<K extends Key<K>> {

    /**
     * Executes the operation for the given keys
     * 
     * @param keys the keys
     */
    void execute(Iterable<K> keys);

    boolean requiresKeys();

}
