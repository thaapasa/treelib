package fi.hut.cs.treelib;

import fi.hut.cs.treelib.internal.TreeOperations;

public interface MVTreeOperations<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>>
    extends TreeOperations<K, V, P> {

}
