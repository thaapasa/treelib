package fi.hut.cs.treelib.common;

import java.nio.ByteBuffer;
import java.util.Random;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.internal.KeyRangeImpl;

/**
 * Key class for keys based on long values.
 * 
 * @author thaapasa
 */
public class LongKey implements Key<LongKey> {

    /** Minimum possible key value. */
    public static final LongKey MIN_KEY = new LongKey(Long.MIN_VALUE);
    /** Maximum possible key value. */
    public static final LongKey MAX_KEY = new LongKey(Long.MAX_VALUE);

    public static final KeyRange<LongKey> ENTIRE_RANGE = new KeyRangeImpl<LongKey>(MIN_KEY,
        MAX_KEY);

    public static final String MIN_KEY_STR = "-\u221e";
    public static final String MAX_KEY_STR = "\u221e";

    public static final LongKey PROTOTYPE = new LongKey();

    /** The actual key value. */
    private final Long value;

    /**
     * Creates a prototype of this class. Prototypes must not be used in
     * comparisons.
     */
    protected LongKey() {
        this.value = null;
    }

    /**
     * Creates a proper instance of this class for the given integer value.
     * 
     * @param key the key value
     */
    public LongKey(long key) {
        this.value = new Long(key);
    }

    public long longValue() {
        return value;
    }

    /**
     * Returns the maximum possible key value.
     */
    @Override
    public LongKey getMaxKey() {
        return MAX_KEY;
    }

    /**
     * Returns the minimum possible key value.
     */
    @Override
    public LongKey getMinKey() {
        return MIN_KEY;
    }

    @Override
    public KeyRange<LongKey> getEntireRange() {
        return ENTIRE_RANGE;
    }

    @Override
    public String write() {
        if (value == null)
            return "null";
        if (value == Long.MAX_VALUE)
            return MAX_LOG_STR;
        else if (value == Long.MIN_VALUE)
            return MIN_LOG_STR;
        else
            return String.valueOf(value);
    }

    @Override
    public String toString() {
        if (value == null)
            return "null";
        if (value == Long.MAX_VALUE)
            return MAX_KEY_STR;
        else if (value == Long.MIN_VALUE)
            return MIN_KEY_STR;
        else
            return String.valueOf(value);
    }

    @Override
    public LongKey nextKey() {
        // Already at maximum value, so can't increase
        if (value == Long.MAX_VALUE)
            return this;
        return new LongKey(value + 1);
    }

    @Override
    public LongKey previousKey() {
        // Already at minimum value, so can't increase
        if (value == Long.MIN_VALUE)
            return this;
        return new LongKey(value - 1);
    }

    @Override
    public boolean isPrototype() {
        return value == null;
    }

    @Override
    public LongKey abs() {
        return value < 0 ? new LongKey(-value) : this;
    }

    @Override
    public LongKey subtract(LongKey other) {
        return new LongKey(value - other.value);
    }

    @Override
    public LongKey add(LongKey other) {
        return new LongKey(value + other.value);
    }

    @Override
    public LongKey divide(LongKey divider) {
        return new LongKey(value / divider.value);
    }

    @Override
    public LongKey multiply(LongKey multiplier) {
        return new LongKey(value * multiplier.value);
    }

    @Override
    public LongKey multiply(double multiplier) {
        return new LongKey((long) (value * multiplier));
    }

    @Override
    public LongKey fromInt(int value) {
        return new LongKey(value);
    }

    @Override
    public float toFloat() {
        return value.floatValue();
    }

    @Override
    public int toInt() {
        return (int) value.longValue();
    }

    @Override
    public int compareTo(LongKey o) {
        return value.compareTo(o.value);
    }

    @Override
    public boolean isValid(String value) {
        try {
            Long.parseLong(value);
            return value != null;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public LongKey parse(String value) {
        if (value == null)
            return null;
        if (value.equals(MIN_LOG_STR) || value.equals(MIN_KEY_STR)) {
            return MIN_KEY;
        } else if (value.equals(MAX_LOG_STR) || value.equals(MAX_KEY_STR)) {
            return MAX_KEY;
        } else {
            return new LongKey(Long.parseLong(value));
        }
    }

    @Override
    public String write(LongKey o) {
        return o.write();
    }

    @Override
    public int getByteDataSize() {
        // Size is 8 bytes
        return Long.SIZE / 8;
    }

    @Override
    public LongKey readFromBytes(ByteBuffer byteArray) {
        long value = byteArray.getLong();
        return new LongKey(value);
    }

    @Override
    public void writeToBytes(ByteBuffer byteArray) {
        byteArray.putLong(value.longValue());
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LongKey))
            return false;
        LongKey k = (LongKey) o;
        return value.equals(k.value);
    }

    @Override
    public LongKey random(LongKey min, LongKey max, Random random) {
        long range = (long) max.value - (long) min.value;
        long value = (long) (random.nextDouble() * range);
        int res = (int) (value + min.value);
        assert res >= min.value : res + " < " + min;
        assert res <= max.value : res + " > " + max;
        return new LongKey(res);
    }

}
