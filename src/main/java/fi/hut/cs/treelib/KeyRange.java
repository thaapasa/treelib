package fi.hut.cs.treelib;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.internal.KeyRangeImpl;

/**
 * Models key ranges of the form [min, max). The key range is immutable (like
 * Strings), so you do not need to worry about sharing references to the same
 * key range.
 * 
 * The key range can be easily changed to support different end-key inclusion
 * criteria (open-closed etc.). However, currently the key range is always
 * closed-open.
 * 
 * @author thaapasa
 */
public abstract class KeyRange<K extends Key<K>> {

    private static final Logger log = Logger.getLogger(KeyRange.class);

    private final K min;
    private final K max;
    private final boolean singleKey;

    protected KeyRange(K min, K max, boolean allowSameCoords) {
        assert min != null;
        assert max != null;

        if (min.isPrototype()) {
            this.min = min;
            this.max = min;
            this.singleKey = true;
        } else {
            int order = min.compareTo(max);
            if (!allowSameCoords) {
                assert order != 0;
            }
            if (order <= 0) {
                // OK, in correct order
                this.min = min;
                this.max = max;
            } else {
                log.warn(String.format(
                    "Constructing a range with parameters min > max (%s > %s)", min, max));
                this.min = max;
                this.max = min;
            }
            this.singleKey = false;
        }
    }

    protected KeyRange(K min, K max) {
        this(min, max, false);
    }

    protected KeyRange(KeyRange<K> other) {
        this.min = other.min;
        this.max = other.max;
        this.singleKey = other.singleKey;
    }

    protected KeyRange(K onlyKey) {
        assert onlyKey != null;
        this.min = onlyKey;
        this.max = onlyKey;
        this.singleKey = true;
    }

    public boolean isSingleKey() {
        return singleKey;
    }

    public KeyRange<K> getEntireKeyRange() {
        return new KeyRangeImpl<K>(min.getMinKey(), min.getMaxKey());
    }

    public K getMiddle() {
        K middle = min.add(max).multiply(0.5d);
        return middle;
    }

    public boolean intersects(KeyRange<K> other) {
        if (this.singleKey)
            return other.contains(this.min);
        if (other.singleKey)
            return this.contains(other.min);
        return min.compareTo(other.max) < 0 && max.compareTo(other.min) > 0;
    }

    /**
     * @return true if the given key is contained in this range
     */
    public boolean contains(K key) {
        return singleKey ? min.equals(key) : min.compareTo(key) <= 0 && key.compareTo(max) < 0;
    }

    public boolean isEmpty() {
        return !singleKey && min.compareTo(max) == 0;
    }

    /**
     * Convenience method for testing if the range contains the given key
     * 
     * @param keyString key, in its string representation
     */
    public boolean contains(String keyString) {
        K key = min.parse(keyString);
        return contains(key);
    }

    protected abstract KeyRange<K> instantiate(K min, K max);

    protected abstract KeyRange<K> instantiate(K onlyKey);

    public KeyRange<K> extend(K key) {
        if (singleKey) {
            if (min.equals(key))
                return this;
            return (min.compareTo(key) < 0) ? instantiate(min, key.nextKey()) : instantiate(key,
                min.nextKey());
        }
        if (contains(key)) {
            return this;
        }
        K newMin = min;
        K newMax = max;
        // if (key < min) {
        if (key.compareTo(min) < 0) {
            newMin = key;
        } else {
            // assert key >= max;
            assert key.compareTo(max) >= 0;
            newMax = key.nextKey();
        }
        KeyRange<K> newRange = instantiate(newMin, newMax);
        assert newRange.contains(key);
        return newRange;
    }

    /**
     * @return the intersection; or null, if the given ranges do not intersect
     */
    public KeyRange<K> intersection(KeyRange<K> o) {
        if (singleKey) {
            return o.contains(min) ? this : null;
        }
        if (!overlaps(o))
            return null;

        // K newMin = o.min > min ? o.min : min;
        K newMin = o.min.compareTo(min) > 0 ? o.min : min;
        // K newMax = o.max < max ? o.max : max;
        K newMax = o.max.compareTo(max) < 0 ? o.max : max;

        KeyRange<K> newRange = instantiate(newMin, newMax);
        assert contains(newRange) : this + " does not contain " + newRange
            + ", which is int. with " + o;
        assert o.contains(newRange) : o + " does not contain " + newRange
            + ", which is int. with " + this;
        return newRange;
    }

    public K getMin() {
        return min;
    }

    public K getMax() {
        return max;
    }

    /**
     * @return true if this range completely contains the given range
     */
    public boolean contains(KeyRange<K> o) {
        if (singleKey) {
            return o.singleKey && min.equals(o.min);
        }
        // o.min >= min && o.max <= max
        return o.min.compareTo(min) >= 0 && o.max.compareTo(max) <= 0;
    }

    public boolean overlaps(KeyRange<K> o) {
        if (singleKey)
            return o.contains(min);
        // if (min < o.min)
        if (min.compareTo(o.min) < 0) {
            // This starts before o
            // return max > o.min;
            return max.compareTo(o.min) > 0;
        }
        // if (o.min < min)
        else if (o.min.compareTo(min) < 0) {
            // o starts before this
            // return o.max > min
            return o.max.compareTo(min) > 0;
        }
        // Same min key, must overlap
        return true;
    }

    public int compareTo(KeyRange<K> o) {
        if (o == null) {
            return 1;
        }

        // Do comparison based on the min key
        int minC = min.compareTo(o.min);
        if (minC != 0) {
            return minC;
        }
        // Min key is equal, compare with max keys
        return max.compareTo(o.max);
    }

    /**
     * Returns the distance between the keys (that is, max - min).
     */
    public K getDistance() {
        return getMax().subtract(getMin());
    }

    @Override
    public int hashCode() {
        return min.hashCode();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (!(o instanceof KeyRange)) {
            return false;
        }
        try {
            KeyRange<K> range = (KeyRange<K>) o;
            return singleKey == range.singleKey && min.equals(range.min) && max.equals(range.max);
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return singleKey ? getMin().toString() : String.format("[%s,%s)", getMin().toString(),
            getMax().toString());
    }

    public int getByteDataSize() {
        return min.getByteDataSize() + max.getByteDataSize();
    }

    public KeyRange<K> readFromBytes(ByteBuffer byteArray) {
        K minV = min.readFromBytes(byteArray);
        K maxV = max.readFromBytes(byteArray);

        return minV.equals(maxV) ? instantiate(minV) : instantiate(minV, maxV);
    }

    public void writeToBytes(ByteBuffer byteArray) {
        min.writeToBytes(byteArray);
        max.writeToBytes(byteArray);
    }

}
