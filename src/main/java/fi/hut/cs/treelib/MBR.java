package fi.hut.cs.treelib;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Random;

import fi.hut.cs.treelib.internal.KeyParser;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.util.KeyUtils;
import fi.tuska.util.AssertionSupport;
import fi.tuska.util.NotImplementedException;

/**
 * Models MBRs, or minimum bounding rectangles (or boxes, or whatever). These
 * keys are ordered as per the J-tree specs (first order by X-min, Y-min,
 * Z-min..., then X-max, Y-max, Z-max...). R-trees can use this too; they do
 * not have to use the ordering function.
 * 
 * @author thaapasa
 * 
 * @param <K>
 */
public class MBR<K extends Key<K>> extends KeyRange<Coordinate<K>> implements Key<MBR<K>> {

    private final K proto;
    private final int dimensions;
    private final boolean isPrototype;
    private final K zeroKey;

    /**
     * Paired order means that the keys are paired lo, hi. When pairedOrder is
     * true, parsing expects the keys to be in order: x1, x2, y1, y2... When
     * pairedOrder is false, parsing expects the keys to be in order: x1,
     * y1..., x2, y2...
     */
    private final boolean pairedOrder;

    /**
     * Creates a prototype of this MBR.
     * 
     * @param proto the single key prototype.
     * @param pairedOrder when true, key order needs to be Xmin, Xmax, Ymin,
     * Ymax, ...
     */
    public MBR(Integer dimensions, K proto, Boolean pairedOrder) {
        super(new Coordinate<K>(dimensions, proto));
        this.proto = proto;
        this.dimensions = dimensions;
        this.isPrototype = true;
        this.pairedOrder = pairedOrder;
        this.zeroKey = proto.parse("0");
    }

    /**
     * Creates a new MBR.
     * 
     * Note that the parameters are checked and ordered in the correct order.
     * 
     * Paired order is assumed (and forced to parsing).
     * 
     * @param keys the keys, in order Xmin, Xmax, Ymin, Ymax, Zmin, Zmax ...
     */
    public MBR(K... keys) {
        this(getKeys(true, keys), getKeys(false, keys), true);
    }

    /**
     * @return an MBR covering the entire key area
     */
    public MBR<K> getEntireArea() {
        return instantiate(proto.getMinKey(), proto.getMaxKey());
    }

    /**
     * Creates a new MBR.
     * 
     * Note that the parameters need to be in correct order.
     */
    public MBR(Coordinate<K> minCoord, Coordinate<K> maxCoord, boolean pairedOrder) {
        super(minCoord, maxCoord, true);

        this.pairedOrder = pairedOrder;
        if (minCoord.getDimensions() != maxCoord.getDimensions()) {
            this.isPrototype = true;
            throw new IllegalArgumentException("Dimensions do not match: "
                + minCoord.getDimensions() + " vs " + maxCoord.getDimensions());
        }

        this.dimensions = minCoord.getDimensions();
        this.proto = minCoord.get(0);
        this.isPrototype = proto.isPrototype();
        this.zeroKey = proto.parse("0");

        if (AssertionSupport.isAssertionsEnabled() && !isPrototype) {
            // Check that coordinates are in correct order
            for (int i = 0; i < dimensions; i++) {
                // minCoord <= maxCoord
                assert minCoord.get(i).compareTo(maxCoord.get(i)) <= 0 : i + ": "
                    + minCoord.get(i) + " > " + maxCoord.get(i);
            }
        }
    }

    /**
     * Creates a new MBR.
     * 
     * Note that the parameters need to be in correct order.
     */
    public MBR(K[] minCoord, K[] maxCoord, boolean pairedOrder) {
        this(new Coordinate<K>(minCoord), new Coordinate<K>(maxCoord), pairedOrder);
    }

    @SuppressWarnings("unchecked")
    private static <K extends Key<K>> K[] getKeys(boolean min, K... keys) {
        if (keys == null || keys.length == 0)
            throw new IllegalArgumentException("No keys given for MBR");
        if (keys.length % 2 != 0)
            throw new IllegalArgumentException("Odd number of keys for an MBR");
        int dimensions = keys.length / 2;
        K[] coords = (K[]) Array.newInstance(keys[0].getClass(), dimensions);
        for (int i = 0; i < dimensions; i++) {
            int i1 = i * 2;
            int i2 = i1 + 1;
            if (keys[i1].compareTo(keys[i2]) < 0) {
                // Correct order
                coords[i] = min ? keys[i1] : keys[i2];
            } else {
                coords[i] = min ? keys[i2] : keys[i1];
            }
        }
        return coords;
    }

    /**
     * Checks if this MBR overlaps with the given MBR. Overlapping is true
     * even when only borders overlap. For example, <(0,10),(0,10)> overlaps
     * with <(10,20),(0,10)> even though their union is a line (empty area).
     * 
     * @param other the other MBR
     * @return true if the MBRs overlap each other in any way.
     */
    public boolean overlaps(MBR<K> other) {
        if (isPrototype || other.isPrototype)
            throw new UnsupportedOperationException("Prototypes cannot be compared");
        if (other.dimensions != dimensions)
            throw new IllegalArgumentException("Different MBR dimensions: " + dimensions
                + " and " + other.dimensions);

        for (int i = 0; i < dimensions; i++) {
            // if (this.low[i] > other.high[i] || this.high[i] < other.low[i])
            if (getLow(i).compareTo(other.getHigh(i)) > 0
                || getHigh(i).compareTo(other.getLow(i)) < 0)
                return false;
        }
        return true;
    }

    /**
     * Contains means that key is totally inside this MBR (borders may be
     * same).
     * 
     * @param other the other MBR
     * @return true if the given MBR is wholly inside this MBR
     */
    public boolean contains(MBR<K> other) {
        if (isPrototype || other.isPrototype)
            throw new UnsupportedOperationException("Prototypes cannot be compared");

        for (int i = 0; i < dimensions; i++) {
            // if low <= other.low && high >= other.high, then may contain ->
            // if low > other.low || high < other.high, then does not contain
            if ((getLow(i).compareTo(other.getLow(i))) > 0
                || getHigh(i).compareTo(other.getHigh(i)) < 0)
                return false;
        }
        return true;
    }

    public MBR<K> extend(MBR<K> other) {
        if (isPrototype || other.isPrototype)
            throw new UnsupportedOperationException("Prototypes cannot be extended");

        if (contains(other))
            return this;

        K[] newMin = createArray();
        K[] newMax = createArray();
        for (int i = 0; i < dimensions; i++) {
            newMin[i] = getLow(i);
            if (other.getLow(i).compareTo(newMin[i]) < 0)
                newMin[i] = other.getLow(i);

            newMax[i] = getHigh(i);
            if (other.getHigh(i).compareTo(newMax[i]) > 0)
                newMax[i] = other.getHigh(i);
        }
        return new MBR<K>(newMin, newMax, pairedOrder);
    }

    /**
     * Counts the enlargement area required to contain the given MBR. If this
     * MBR already contains the given MBR, then returns the difference of the
     * areas (as a negative number).
     * 
     * @param mbr the MBR to contain.
     * @return the enlargement area required to contain the given MBR.
     */
    public K countEnlargement(MBR<K> mbr) {
        K mbrVal = null;
        if (contains(mbr)) {
            mbrVal = mbr.getArea().subtract(getArea());
            assert mbrVal.toFloat() <= 0;
        } else {
            MBR<K> enlarged = extend(mbr);
            mbrVal = enlarged.getArea().subtract(getArea());
            assert mbrVal.toFloat() >= 0;
        }
        return mbrVal;
    }

    /**
     * Counts the overlap area between this MBR and the given MBR; or zero, if
     * the MBRs do not overlap.
     * 
     * @param mbr the other MBR.
     * @return the area of overlap between this and MBR; or zero.
     */
    public K countOverlapArea(MBR<K> other) {
        if (!overlaps(other)) {
            // Does not overlap the other MBR
            return zeroKey;
        }

        MBR<K> intMBR = intersection(other);
        assert intMBR != null;

        return intMBR.getArea();
    }

    /**
     * @return the intersection of this MBR and another MBR; null, if the MBRs
     * do not even touch. The area of the MBR will be zero if the MBRs only
     * touch on edges (but the result != null), even if only in one point.
     */
    public MBR<K> intersection(MBR<K> other) {
        if (!overlaps(other))
            return null;

        K[] minKeys = createArray();
        K[] maxKeys = createArray();
        for (int i = 0; i < dimensions; i++) {
            minKeys[i] = KeyUtils.max(getMin().get(i), other.getMin().get(i));
            maxKeys[i] = KeyUtils.min(getMax().get(i), other.getMax().get(i));
        }
        return new MBR<K>(minKeys, maxKeys, pairedOrder);
    }

    @Override
    public int hashCode() {
        if (dimensions == 0)
            return proto.hashCode();
        return getMin().hashCode();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (!(o instanceof MBR))
            return false;
        MBR<K> m = (MBR<K>) o;
        return compareTo(m) == 0;
    }

    public int getDimensions() {
        return dimensions;
    }

    public K getLow(int dim) {
        return getMin().get(dim);
    }

    public K getHigh(int dim) {
        return getMax().get(dim);
    }

    @Override
    public MBR<K> fromInt(int value) {
        Coordinate<K> min = getMin().fromInt(value);
        Coordinate<K> max = getMin().fromInt(value + 10);
        return new MBR<K>(min, max, pairedOrder);
    }

    @Override
    public KeyRange<MBR<K>> getEntireRange() {
        return new KeyRangeImpl<MBR<K>>(getMinKey(), getMaxKey());
    }

    /**
     * // TODO: return K's or doubles?
     * 
     * @return an approximation of the distance between the endpoints in the
     * given dimension
     */
    public K getDistance(int dimension) {
        if (isPrototype)
            throw new UnsupportedOperationException("Prototypes do not have distances");
        if (dimension < 0 || dimension >= dimensions)
            throw new IndexOutOfBoundsException("Dimension " + dimension + " out of bounds");
        K dist = getMax().get(dimension).subtract(getMin().get(dimension));
        assert dist.toFloat() >= 0 : dist;
        return dist;
    }

    /**
     * In 1D MBRs, returns the length of the line. With 2D MBRs, returns the
     * perimeter of the rectangle. With 3D MBRs, returns the sum of the length
     * of the lines that draw the box.
     * 
     * <p>
     * Uses the formula result = sum<sub>i</sub> 2<sup>dim-1</sup>
     * distance<sub>i</sub>.
     * 
     * @return the perimeter of the MBR.
     */
    public K getPerimeter() {
        K res = zeroKey;
        int fact = 1 << (dimensions - 1);
        for (int i = 0; i < dimensions; i++) {
            for (int k = 0; k < fact; k++) {
                res = res.add(getDistance(i));
            }
        }
        return res;
    }

    /**
     * @return an approximation of the area covered by this MBR (for
     * 2-dimensional MBRs); the distance for 1-dimensional MBRs; the volume
     * for 3-dimensional ones, and so on.
     */
    public K getArea() {
        if (isPrototype)
            throw new UnsupportedOperationException("Prototypes do not have an area");
        if (isEmpty())
            return zeroKey;
        K total = null;
        for (int i = 0; i < dimensions; i++) {
            total = total != null ? total.multiply(getDistance(i)) : getDistance(i);
            // total *= getDistance(i);
        }
        return total;
    }

    /**
     * @return maximum MBR, with keys [max, max, max, ...]
     */
    @Override
    public MBR<K> getMaxKey() {
        K max = proto.getMaxKey();
        return instantiate(max, max);
    }

    /**
     * @return minimum MBR, with keys [min, min, min, ...]
     */
    @Override
    public MBR<K> getMinKey() {
        K min = proto.getMinKey();
        return instantiate(min, min);
    }

    @SuppressWarnings("unchecked")
    private K[] createArray() {
        return (K[]) Array.newInstance(proto.getClass(), dimensions);
    }

    @Override
    public MBR<K> nextKey() {
        if (isPrototype)
            throw new UnsupportedOperationException("nextKey() not supported for prototypes");

        Coordinate<K> min = getMin();
        Coordinate<K> max = getMax();

        // Try to increase max key. New max key cannot have coordinates below
        // the min key.
        Coordinate<K> nextMax = max.nextKey(min, null);
        // If max key can be increased (within the bounds of min), then we are
        // done
        if (!nextMax.equals(max))
            return new MBR<K>(min.getCoordinates(), nextMax.getCoordinates(), pairedOrder);

        // Try to increase min key, no limits as we are increasing the min
        // point
        Coordinate<K> nextMin = min.nextKey();
        // If min key can be increased, then ok
        // We want the minimum key with the new min key
        if (!nextMin.equals(min))
            return new MBR<K>(nextMin.getCoordinates(), nextMin.getCoordinates(), pairedOrder);

        // Cannot increase
        return this;
    }

    @Override
    public MBR<K> previousKey() {
        if (isPrototype)
            throw new UnsupportedOperationException("previousKey() not supported for prototypes");

        Coordinate<K> min = getMin();
        Coordinate<K> max = getMax();

        // Try to decrease max key. New max key cannot have coordinates below
        // the min key.
        Coordinate<K> prevMax = max.previousKey(min, null);
        // If max key can be decreased, then ok
        if (!prevMax.equals(max))
            return new MBR<K>(min.getCoordinates(), prevMax.getCoordinates(), pairedOrder);

        // Try to decrease min key, no limits as we are decreasing the min key
        Coordinate<K> prevMin = min.previousKey();
        // If min key can be decreased, then ok
        if (!prevMin.equals(min))
            return new MBR<K>(prevMin.getCoordinates(), max.getMaxKey().getCoordinates(),
                pairedOrder);

        // Cannot decrease
        return this;
    }

    @Override
    public int toInt() {
        return getLow(0).toInt();
    }

    @Override
    public int compareTo(MBR<K> o) {
        // First compare by min keys
        int comp = getMin().compareTo(o.getMin());
        if (comp != 0)
            return comp;
        // Then by max keys
        return getMax().compareTo(o.getMax());
    }

    @Override
    public boolean isValid(String value) {
        return parse(value) != null;
    }

    @Override
    public MBR<K> parse(String value) {
        String[] parts = KeyParser.parseKeys(dimensions * 2, value);

        if (parts == null)
            return null;

        K[] minKeys = createArray();
        K[] maxKeys = createArray();

        for (int i = 0; i < dimensions; i++) {
            K min = null;
            K max = null;
            if (pairedOrder) {
                // Keys are in order Xmin, Xmax, Ymin, Ymax, ...
                min = proto.parse(parts[i * 2]);
                max = proto.parse(parts[i * 2 + 1]);
            } else {
                // Keys are in order Xmin, Ymin, ... Xmax, Ymax, ...
                min = proto.parse(parts[i]);
                max = proto.parse(parts[dimensions + i]);
            }
            if (min == null || max == null)
                return null;

            if (min.compareTo(max) < 0) {
                // Coordinates are in correct order
                minKeys[i] = min;
                maxKeys[i] = max;
            } else {
                // Fix the order
                minKeys[i] = max;
                maxKeys[i] = min;
            }
        }

        return new MBR<K>(minKeys, maxKeys, pairedOrder);
    }

    @Override
    public String write(MBR<K> object) {
        return object.write();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{");
        if (pairedOrder) {
            for (int i = 0; i < dimensions; i++) {
                if (i != 0)
                    buf.append(",");
                buf.append("(").append(getMin().get(i).toString()).append(":");
                buf.append(getMax().get(i).toString()).append(")");
            }
        } else {
            buf.append(getMin().toString());
            buf.append(":");
            buf.append(getMax().toString());
        }
        buf.append("}");
        return buf.toString();
    }

    @Override
    public String write() {
        StringBuilder buf = new StringBuilder();
        buf.append("{");
        if (pairedOrder) {
            for (int i = 0; i < dimensions; i++) {
                if (i != 0)
                    buf.append(",");
                buf.append("(").append(getMin().get(i).write()).append(":");
                buf.append(getMax().get(i).write()).append(")");
            }
        } else {
            buf.append(getMin().write());
            buf.append(":");
            buf.append(getMax().write());
        }
        buf.append("}");
        return buf.toString();
    }

    @Override
    public int getByteDataSize() {
        int size = 0;
        // Then store the values
        int valueSize = proto.getByteDataSize();
        // Min and max (2) values of each dimension
        size += 2 * dimensions * valueSize;
        return size;
    }

    @Override
    public MBR<K> readFromBytes(ByteBuffer data) {
        K[] minKeys = createArray();
        K[] maxKeys = createArray();
        for (int i = 0; i < dimensions; i++) {
            minKeys[i] = proto.readFromBytes(data);
            maxKeys[i] = proto.readFromBytes(data);
        }
        return new MBR<K>(minKeys, maxKeys, pairedOrder);
    }

    @Override
    public void writeToBytes(ByteBuffer data) {
        K[] minKeys = getMin().getCoordinates();
        assert minKeys != null;
        K[] maxKeys = getMax().getCoordinates();
        assert maxKeys != null;
        for (int i = 0; i < dimensions; i++) {
            if (!isPrototype) {
                assert minKeys[i].compareTo(maxKeys[i]) <= 0 : minKeys[i] + " > " + maxKeys[i]
                    + ", " + i + ": " + this;
            }
            minKeys[i].writeToBytes(data);
            maxKeys[i].writeToBytes(data);
        }
    }

    @Override
    public float toFloat() {
        throw new UnsupportedOperationException("MBRs cannot be converted to floats");
    }

    @Override
    protected MBR<K> instantiate(Coordinate<K> min, Coordinate<K> max) {
        return new MBR<K>(min.getCoordinates(), max.getCoordinates(), pairedOrder);
    }

    @Override
    protected MBR<K> instantiate(Coordinate<K> onlyKey) {
        return new MBR<K>(onlyKey.getCoordinates(), onlyKey.getCoordinates(), pairedOrder);
    }

    private MBR<K> instantiate(K min, K max) {
        K[] newMin = createArray();
        K[] newMax = createArray();
        for (int i = 0; i < dimensions; i++) {
            newMin[i] = min;
            newMax[i] = max;
        }
        return new MBR<K>(newMin, newMax, pairedOrder);
    }

    @Override
    public boolean isPrototype() {
        return isPrototype;

    }

    @Override
    public MBR<K> abs() {
        throw new NotImplementedException();
    }

    @Override
    public MBR<K> subtract(MBR<K> other) {
        Coordinate<K> min = getMin().subtract(other.getMin());
        Coordinate<K> max = getMax().subtract(other.getMax());
        return new MBR<K>(min, max, pairedOrder);
    }

    @Override
    public MBR<K> add(MBR<K> other) {
        Coordinate<K> min = getMin().add(other.getMin());
        Coordinate<K> max = getMax().add(other.getMax());
        return new MBR<K>(min, max, pairedOrder);
    }

    /**
     * Returns a random key value between min and max, inclusive. Min should
     * contain the minimum size of the MBR, and max the maximum size. Also,
     * this object (whose method is called) defines the bounds for the
     * resulting MBR.
     * 
     * @param random the random generator to use
     * @return the random key value
     */
    @Override
    public MBR<K> random(MBR<K> min, MBR<K> max, Random random) {
        K[] resMin = createArray();
        K[] resMax = createArray();

        // Go through each dimension
        for (int i = 0; i < dimensions; i++) {
            K maxSize = max.getDistance(i);
            K minSize = min.getDistance(i);
            // Randomize size along this dimension
            K size = proto.random(minSize, maxSize, random);
            // Get minimum bounds for coordinate
            K minCoord = getMin().get(i);
            K maxCoord = getMax().get(i).subtract(size);
            // Randomize min coord
            resMin[i] = proto.random(minCoord, maxCoord, random);
            // Max coord is min + size
            resMax[i] = resMin[i].add(size);
        }

        return new MBR<K>(resMin, resMax, pairedOrder);
    }

    @Override
    public MBR<K> divide(MBR<K> divider) {
        Coordinate<K> min = getMin().divide(divider.getMin());
        Coordinate<K> max = getMax().divide(divider.getMax());
        return new MBR<K>(min, max, pairedOrder);
    }

    @Override
    public MBR<K> multiply(MBR<K> multiplier) {
        Coordinate<K> min = getMin().multiply(multiplier.getMin());
        Coordinate<K> max = getMax().multiply(multiplier.getMax());
        return new MBR<K>(min, max, pairedOrder);
    }

    @Override
    public MBR<K> multiply(double multiplier) {
        Coordinate<K> min = getMin().multiply(multiplier);
        Coordinate<K> max = getMax().multiply(multiplier);
        return new MBR<K>(min, max, pairedOrder);
    }

}
