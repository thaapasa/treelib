package fi.hut.cs.treelib.common;

import fi.hut.cs.treelib.Owner;

/**
 * An implementation of the owner interface. To be used when transactions are
 * not available (from GUI, for DB internal operations, etc.)
 * 
 * @author thaapasa
 */
public class OwnerImpl implements Owner {

    private static int lastOwnerID = 0;

    private final int ownerID;
    private final String name;

    public OwnerImpl(String name) {
        this.ownerID = createID();
        this.name = name;
    }

    /**
     * Creates a new unique ID for owners. Used by all owner implementations.
     * 
     * @return a new unique owner ID
     */
    public static int createID() {
        return ++lastOwnerID;
    }

    public static int getLastUsedID() {
        return lastOwnerID;
    }

    public static void resetID(int id) {
        lastOwnerID = Math.max(id, lastOwnerID);
    }

    @Override
    public int getOwnerID() {
        return ownerID;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("Owner: %s (%d)", name, ownerID);
    }

}
