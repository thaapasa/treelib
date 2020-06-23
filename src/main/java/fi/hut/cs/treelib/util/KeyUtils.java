package fi.hut.cs.treelib.util;

import fi.hut.cs.treelib.Key;

/**
 * Convenience methods for handling Key implementing classes.
 * 
 * @author thaapasa
 */
public class KeyUtils {

    private KeyUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * @return the minimum of the two given keys.
     */
    public static <K extends Key<K>> K min(K a, K b) {
        // return (a < b) a : b;
        return (a.compareTo(b) < 0) ? a : b;
    }

    /**
     * @return the maximum of the two given keys.
     */
    public static <K extends Key<K>> K max(K a, K b) {
        // return (a > b) a : b;
        return (a.compareTo(b) > 0) ? a : b;
    }
}
