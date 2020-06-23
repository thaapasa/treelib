package fi.hut.cs.treelib;

/**
 * Base interface for multiversion databases.
 * 
 * @author thaapasa
 */
public interface MVDatabase<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>>
    extends Database<K, V, P> {

    @Override
    MVTree<K, V, P> getDatabaseTree();

}
