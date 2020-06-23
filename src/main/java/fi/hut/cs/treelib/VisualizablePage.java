package fi.hut.cs.treelib;

import java.util.List;

/**
 * For visualization: extra, unsafe functions (will return unfixed pages).
 * 
 * @author tuska
 */
public interface VisualizablePage<K extends Key<K>, V extends PageValue<?>> extends Page<K, V> {

    /**
     * @return the child nodes of this node. This method is usable only for
     * visualization!
     */
    List<VisualizablePage<K, V>> getChildren();

}
