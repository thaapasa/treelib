package fi.hut.cs.treelib;

import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;

/**
 * Interface for multidimensional databases. Current implementations: J-tree,
 * Hilbert R-tree and R-tree.
 * 
 * @author thaapasa
 */
public interface MDDatabase<K extends Key<K>, V extends PageValue<?>, P extends MDPage<K, V, ?>>
    extends Database<MBR<K>, V, P> {

    void traverseMDPages(Predicate<MBR<K>> predicate, Callback<Page<MBR<K>, V>> operation,
        Owner owner);

    @Override
    MDTree<K, V, P> getDatabaseTree();

    @Override
    MDTransaction<K, V> beginTransaction();

}
