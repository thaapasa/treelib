package fi.hut.cs.treelib;

import java.util.List;

import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.storage.StoredPage;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

/**
 * A generic interface for modeling nodes in a tree. Supports both
 * single-valued and multi-valued nodes.
 * 
 * @author thaapasa
 * 
 * @param <K> the type of the keys (used for sorting in trees that do sorting)
 * @param <V> the type of the stored values (payload)
 */
public interface Page<K extends Key<K>, V extends PageValue<?>> extends StoredPage {

    /**
     * @return the ID of the page
     */
    PageID getPageID();

    /**
     * Returns a descriptive name of the node (for logging and visualization).
     * For example, node id where appropriate.
     * 
     * @return the name of the node
     */
    String getName();

    /**
     * @return short uniquely identifying name
     */
    String getShortName();

    /**
     * Tells the range of keys that should go to this node. This does not say
     * anything about the actual contents (only that they must be inside this
     * range).
     * 
     * @return the key range of this node.
     */
    KeyRange<K> getKeyRange();

    /**
     * Returns the child node that contains the given key. The page is fixed
     * to the buffer and needs to be unfixed by the caller.
     * 
     * @param key the navigation key
     * @return the child of this node that contains the given key
     */
    Page<K, V> getChild(K key, Owner owner);

    /**
     * @return the size (in bytes) required to store a single entry
     */
    int getSingleEntrySize();

    /**
     * Calls the callback for each entry in this page.
     * 
     * @param callback the callback
     * @return true if all entries were processed (all callbacks returned
     * true)
     */
    boolean processEntries(Callback<Pair<KeyRange<K>, PageValue<?>>> callback);

    boolean containsChild(PageID childID);

    /**
     * @return the key with exactly this key (e.g., min key/search key for
     * index pages)
     */
    PageValue<?> getEntry(K key);

    /**
     * @return a list of all entries in this page
     */
    List<PageValue<?>> getEntries();

    /**
     * Finds a child from the children.
     */
    PageID findChildPointer(K key);

    /**
     * @return true if this node is a leaf node (e.g., has no children).
     */
    boolean isLeafPage();

    /**
     * A leaf node is at height 1. A parent node which only has leaf nodes for
     * children is of height 2. A node with mixed child nodes is at height
     * (max child height) + 1.
     * 
     * <p>
     * This is so that the tree's height is the same as the root node's height
     * (or zero, if there is no root node).
     * 
     * @return the height of this node
     */
    public int getHeight();

    /**
     * Formats the page to be used in a tree.
     * 
     * @param height the height of the page.
     */
    void format(int height);

    boolean contains(K key);

    boolean isFull();

    int getEntryCount();

    int getPageEntryCapacity();

    double getFillRatio();

    boolean isRoot(PagePath<K, V, ? extends Page<K, V>> path);

}
