package fi.hut.cs.treelib.common;

import java.util.Random;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.internal.KeyRangeImpl;

/**
 * Key class for keys based on integer values.
 * 
 * @author thaapasa
 */
public class IntegerKey extends AbstractIntegerValue<IntegerKey> implements Key<IntegerKey> {

    /** Minimum possible key value. */
    public static final IntegerKey MIN_KEY = new IntegerKey(Integer.MIN_VALUE);
    /** Maximum possible key value. */
    public static final IntegerKey MAX_KEY = new IntegerKey(Integer.MAX_VALUE);

    public static final KeyRange<IntegerKey> ENTIRE_RANGE = new KeyRangeImpl<IntegerKey>(MIN_KEY,
        MAX_KEY);

    public static final String MIN_KEY_STR = "-\u221e";
    public static final String MAX_KEY_STR = "\u221e";

    public static final IntegerKey PROTOTYPE = new IntegerKey();
    private final boolean isPrototype;

    /**
     * Creates a prototype of this class. Prototypes must not be used in
     * comparisons.
     */
    protected IntegerKey() {
        super(null);
        this.isPrototype = true;
    }

    /**
     * Creates a proper instance of this class for the given integer value.
     * 
     * @param key the key value
     */
    public IntegerKey(Integer key) {
        super(key);
        this.isPrototype = false;
    }

    /**
     * Returns the maximum possible key value.
     */
    @Override
    public IntegerKey getMaxKey() {
        return MAX_KEY;
    }

    /**
     * Returns the minimum possible key value.
     */
    @Override
    public IntegerKey getMinKey() {
        return MIN_KEY;
    }

    @Override
    public KeyRange<IntegerKey> getEntireRange() {
        return ENTIRE_RANGE;
    }

    @Override
    public String toString() {
        int key = intValue();
        if (key == Integer.MAX_VALUE)
            return MAX_KEY_STR;
        else if (key == Integer.MIN_VALUE)
            return MIN_KEY_STR;
        else
            return String.valueOf(key);
    }

    @Override
    public IntegerKey parse(String value) {
        if (value == null)
            return null;
        if (value.equals(MIN_LOG_STR) || value.equals(MIN_KEY_STR)) {
            return MIN_KEY;
        } else if (value.equals(MAX_LOG_STR) || value.equals(MAX_KEY_STR)) {
            return MAX_KEY;
        } else {
            Integer val = INTEGER_PARSER.parse(value);
            return instantiate(val);
        }
    }

    @Override
    public IntegerKey nextKey() {
        // Already at maximum value, so can't increase
        if (intValue == Integer.MAX_VALUE)
            return this;
        return new IntegerKey(intValue() + 1);
    }

    @Override
    public IntegerKey previousKey() {
        // Already at minimum value, so can't increase
        if (intValue == Integer.MIN_VALUE)
            return this;
        return new IntegerKey(intValue() - 1);
    }

    @Override
    protected IntegerKey instantiate(Integer value) {
        return new IntegerKey(value);
    }

    @Override
    public boolean isPrototype() {
        return isPrototype;
    }

    @Override
    public String write() {
        if (intValue == null)
            return "null";
        if (intValue == MIN_KEY.intValue) {
            return MIN_LOG_STR;
        } else if (intValue == MAX_KEY.intValue) {
            return MAX_LOG_STR;
        } else {
            return INTEGER_PARSER.write(intValue);
        }
    }

    @Override
    public IntegerKey abs() {
        return intValue < 0 ? new IntegerKey(-intValue()) : this;
    }

    @Override
    public IntegerKey subtract(IntegerKey other) {
        return new IntegerKey(intValue - other.intValue);
    }

    @Override
    public IntegerKey add(IntegerKey other) {
        return new IntegerKey(intValue + other.intValue);
    }

    @Override
    public IntegerKey divide(IntegerKey divider) {
        return new IntegerKey(intValue / divider.intValue);
    }

    @Override
    public IntegerKey multiply(IntegerKey multiplier) {
        return new IntegerKey(intValue * multiplier.intValue);
    }

    @Override
    public IntegerKey multiply(double multiplier) {
        return new IntegerKey((int) (intValue * multiplier));
    }

    @Override
    public IntegerKey random(IntegerKey min, IntegerKey max, Random random) {
        long range = (long) max.intValue - (long) min.intValue;
        long value = (long) (random.nextDouble() * range);
        int res = (int) (value + min.intValue());
        assert res >= min.intValue : res + " < " + min;
        assert res <= max.intValue : res + " > " + max;
        return new IntegerKey(res);
    }

}
