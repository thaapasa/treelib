package fi.hut.cs.treelib;

/**
 * This interface represents the owner or the initiator of a given action
 * (query, ...). When DB users call use the underlying tree structures, the
 * owner is the transaction. The tree structures themselves have an owner
 * object that is used when doing internal operations (for example, root page
 * caching).
 * 
 * @author thaapasa
 */
public interface Owner {

    /**
     * Returns the unique owner ID of this object. This ID is used to separate
     * different owners from each other.
     * 
     * @return the unique owner ID
     */
    int getOwnerID();

    /**
     * @return a descriptive name of the owner (for printing, debugging, etc.)
     */
    String getName();

}
