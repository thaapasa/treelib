package fi.hut.cs.treelib;

import java.util.Collection;

/**
 * For visualization: extra, unsafe functions (will return unfixed pages).
 * 
 * @author tuska
 */
public interface VisualizableTree<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>>
    extends Tree<K, V, P> {

    Collection<VisualizablePage<K, V>> getPagesAtHeight(int height);

}
