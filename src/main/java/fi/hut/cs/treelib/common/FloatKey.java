package fi.hut.cs.treelib.common;

import java.nio.ByteBuffer;
import java.util.Random;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.internal.KeyRangeImpl;

/**
 * Key class for keys based on float values.
 * 
 * @author thaapasa
 */
public class FloatKey implements Key<FloatKey> {

    private static final Logger log = Logger.getLogger(FloatKey.class);

    /** Minimum possible key value. */
    public static final FloatKey MIN_KEY = new FloatKey(-Float.MAX_VALUE);
    /** Maximum possible key value. */
    public static final FloatKey MAX_KEY = new FloatKey(Float.MAX_VALUE);

    public static final KeyRange<FloatKey> ENTIRE_RANGE = new KeyRangeImpl<FloatKey>(MIN_KEY,
        MAX_KEY);

    private static final String RANDOM_KEY_STR = "*";

    public static final FloatKey PROTOTYPE = new FloatKey();
    private static final Random RANDOM = new Random();
    private static final int RANDOM_MAX = 1000;

    private static final float STORED_PROTO = Float.NaN;

    /**
     * True if this instance is a prototype used to fetch information about
     * the key range.
     */
    private final boolean isPrototype;
    private final float key;

    /** The actual key value. */

    /**
     * Creates a prototype of this class. Prototypes must not be used in
     * comparisons.
     */
    public FloatKey() {
        this.isPrototype = true;
        this.key = STORED_PROTO;
    }

    /**
     * Creates a proper instance of this class for the given integer value.
     * 
     * @param key the key value
     */
    public FloatKey(float key) {
        this.isPrototype = false;
        this.key = key;
    }

    public int intValue() {
        return (int) key;
    }

    /**
     * Returns the maximum possible key value.
     */
    @Override
    public FloatKey getMaxKey() {
        return MAX_KEY;
    }

    /**
     * Returns the minimum possible key value.
     */
    @Override
    public FloatKey getMinKey() {
        return MIN_KEY;
    }

    @Override
    public KeyRange<FloatKey> getEntireRange() {
        return ENTIRE_RANGE;
    }

    /**
     * Reads in a key from the given string source.
     * 
     * Format: [key], for example: 5
     * 
     * @param source the string containing the string representation of the
     * key value.
     */
    @Override
    public FloatKey parse(String source) {
        if (source == null) {
            return null;
        }
        if (source.equals(MIN_LOG_STR) || source.equals(MIN_KEY_STR)) {
            return MIN_KEY;
        } else if (source.equals(MAX_LOG_STR) || source.equals(MAX_KEY_STR)) {
            return MAX_KEY;
        } else if (source.equals(RANDOM_KEY_STR)) {
            return new FloatKey(RANDOM.nextFloat() * RANDOM_MAX);
        } else {
            try {
                return new FloatKey(Float.parseFloat(source));
            } catch (NumberFormatException e) {
                log.warn("Could not convert " + source + " to FloatKey");
                return null;
            }
        }
    }

    @Override
    public String toString() {
        if (key == Float.MAX_VALUE)
            return MAX_KEY_STR;
        else if (key == -Float.MAX_VALUE)
            return MIN_KEY_STR;
        else
            return String.valueOf(key);
    }

    @Override
    public int compareTo(FloatKey other) {
        if (isPrototype) {
            throw new UnsupportedOperationException(
                "Prototype keys cannot be used in comparisons");
        }
        if (other.isPrototype) {
            throw new UnsupportedOperationException(
                "Prototype keys cannot be used in comparisons");
        }

        return Float.compare(key, other.key);
    }

    @Override
    public boolean equals(Object o) {
        if (isPrototype) {
            throw new UnsupportedOperationException(
                "Prototype keys cannot be used in comparisons");
        }
        if (!(o instanceof FloatKey)) {
            return false;
        }
        FloatKey k = (FloatKey) o;
        return compareTo(k) == 0;
    }

    @Override
    public int hashCode() {
        return new Float(key).hashCode();
    }

    @Override
    public boolean isValid(String value) {
        try {
            parse(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public FloatKey nextKey() {
        if (key == Float.MAX_VALUE)
            return this;
        int bitRepr = Float.floatToIntBits(key);
        return new FloatKey(Float.intBitsToFloat(key >= 0 ? bitRepr + 1 : bitRepr - 1));
    }

    @Override
    public FloatKey previousKey() {
        if (key == -Float.MAX_VALUE)
            return this;
        int bitRepr = Float.floatToIntBits(key);
        return new FloatKey(Float.intBitsToFloat(key >= 0 ? bitRepr - 1 : bitRepr + 1));
    }

    @Override
    public int toInt() {
        return (int) key;
    }

    @Override
    public FloatKey fromInt(int value) {
        return new FloatKey(value);
    }

    @Override
    public int getByteDataSize() {
        // Size is four bytes
        return Float.SIZE / 8;
    }

    @Override
    public void writeToBytes(ByteBuffer data) {
        if (isPrototype)
            assert Float.isNaN(key) : key + " != NaN";
        data.putFloat(key);
    }

    @Override
    public FloatKey readFromBytes(ByteBuffer data) {
        float read = data.getFloat();
        return !Float.isNaN(read) ? new FloatKey(read) : PROTOTYPE;
    }

    @Override
    public String write(FloatKey object) {
        return object.write();
    }

    @Override
    public float toFloat() {
        return key;
    }

    @Override
    public String write() {
        if (key == Float.MAX_VALUE)
            return MAX_LOG_STR;
        else if (key == -Float.MAX_VALUE)
            return MIN_LOG_STR;
        else
            return String.valueOf(key);
    }

    @Override
    public boolean isPrototype() {
        return isPrototype;
    }

    @Override
    public FloatKey abs() {
        return key < 0 ? new FloatKey(-key) : this;
    }

    @Override
    public FloatKey subtract(FloatKey other) {
        return new FloatKey(key - other.key);
    }

    @Override
    public FloatKey add(FloatKey other) {
        return new FloatKey(key + other.key);
    }

    @Override
    public FloatKey divide(FloatKey divider) {
        return new FloatKey(key / divider.key);
    }

    @Override
    public FloatKey multiply(FloatKey multiplier) {
        return new FloatKey(key * multiplier.key);
    }

    @Override
    public FloatKey multiply(double multiplier) {
        return new FloatKey((float) (key * multiplier));
    }

    @Override
    public FloatKey random(FloatKey min, FloatKey max, Random random) {
        double range = (double) max.toFloat() - (double) min.toFloat();
        double value = random.nextDouble() * range;
        float res = (float) (value + min.toFloat());
        assert res >= min.toFloat() : res + " < " + min;
        assert res <= max.toFloat() : res + " > " + max;
        return new FloatKey(res);
    }

}
