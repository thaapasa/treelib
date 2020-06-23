package fi.hut.cs.treelib;

import java.util.List;

/**
 * Pages implementing this interface contain links to other pages. These links
 * can be drawn, for example.
 * 
 * @author thaapasa
 */
public interface LinkPage<K extends Key<K>, V extends PageValue<?>> {

    /**
     * Must only be used by the visualizer program. Will directly fetch the
     * linked node from node buffer (without locking), and will unfix the page
     * so that the visualizer does not need to worry about page fixing.
     * 
     * @return the links to other (peer) pages
     */
    List<Page<K, V>> getLinks();

}
