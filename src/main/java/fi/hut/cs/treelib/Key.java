package fi.hut.cs.treelib;

import java.util.Random;

import fi.tuska.util.StringParser;

/**
 * Implementations of this class must provide a way to construct a prototype
 * of the key, for example, with the default (no-parameter) constructor. The
 * prototype will be used to get minimum and maximum key values.
 * 
 * Keys must be unchangeable, so that copying is not required.
 * 
 * @author thaapasa
 */
public interface Key<K extends Key<K>> extends Comparable<K>, StringParser<K>, Storable<K> {

    public static final String MIN_KEY_STR = "-\u221e";
    public static final String MAX_KEY_STR = "\u221e";

    public static final String MIN_LOG_STR = "-INF";
    public static final String MAX_LOG_STR = "INF";

    /**
     * Returns the minimum key.
     */
    K getMinKey();

    /**
     * Returns the maximum key.
     */
    K getMaxKey();

    /**
     * @return the entire key range
     */
    KeyRange<K> getEntireRange();

    /**
     * @return the next key from this key
     */
    K nextKey();

    /**
     * @return the previous key from this key
     */
    K previousKey();

    /**
     * @return an approximate integer representation, used for drawing the key
     */
    int toInt();

    float toFloat();

    /**
     * @return a key represented by the given integer (used to create keys
     * from screen coordinates)
     */
    K fromInt(int value);

    boolean isPrototype();

    /**
     * @return this - other
     */
    K subtract(K other);

    /**
     * @return this + other
     */
    K add(K other);

    /**
     * @return this / divider
     */
    K divide(K divider);

    /**
     * @return this * multiplier
     */
    K multiply(K multiplier);

    /**
     * @return this * multiplier
     */
    K multiply(double multiplier);

    /**
     * @return the absolute value of this, |this|
     */
    K abs();

    /**
     * Returns a random key value between min and max, inclusive. For
     * coordinates, min should contain the min values for each dimension, and
     * max the max values for each dimension. For MBRs, min should contain the
     * minimum size of the MBR, and max the maximum size. Also, with MBRs, the
     * object whose method is called defines the bounds for the resulting MBR.
     * 
     * @param random the random generator to use
     * @return the random key value
     */
    K random(K min, K max, Random random);

    /**
     * @return a string representation for writing to a log file or some such
     */
    String write();

}
