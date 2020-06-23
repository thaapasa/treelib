package fi.hut.cs.treelib;

/**
 * This interface marks objects that can be stored in the nodes themselves.
 * This is used to mark the index node router values (that is, page IDs), and
 * the actual payload content of the database (leaf page values).
 * 
 * 
 * @type V the stored value in the page
 * 
 * @author thaapasa
 */
public interface PageValue<V> extends Storable<PageValue<V>> {

    V getValue();

    PageValue<V> parse(String source);

    String write();

}
