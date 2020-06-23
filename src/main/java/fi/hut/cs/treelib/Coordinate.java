package fi.hut.cs.treelib;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Random;

import fi.hut.cs.treelib.internal.KeyParser;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.tuska.util.NotImplementedException;

/**
 * A simple coordinate class. Basically stores an array of keys. Ordering is
 * based first on the first component, then the second component, and so on.
 * 
 * @author thaapasa
 * 
 * @param <K>
 */
public class Coordinate<K extends Key<K>> implements Key<Coordinate<K>> {

    private final K[] coordinates;
    private final boolean isPrototype;
    private final int dimensions;
    private final K keyProto;

    public Coordinate(int dimensions, K proto) {
        if (dimensions < 1)
            throw new IllegalArgumentException("Invalid amount of dimensions: " + dimensions);

        this.isPrototype = true;
        this.keyProto = proto;
        this.dimensions = dimensions;
        this.coordinates = createArray();
        for (int i = 0; i < dimensions; i++) {
            this.coordinates[i] = proto;
        }
    }

    public Coordinate(K... coordinates) {
        this.coordinates = coordinates;
        this.dimensions = coordinates.length;
        this.keyProto = coordinates[0];
        this.isPrototype = keyProto.isPrototype();
    }

    public boolean isValid() {
        return !isPrototype;
    }

    public K get(int index) {
        if (isPrototype)
            return keyProto;
        return coordinates[index];
    }

    public int getDimensions() {
        return dimensions;
    }

    protected K[] getCoordinates() {
        return coordinates;
    }

    @SuppressWarnings("unchecked")
    private K[] createArray() {
        return (K[]) Array.newInstance(keyProto.getClass(), dimensions);
    }

    private Coordinate<K> getAllSame(K coord) {
        K[] coords = createArray();
        for (int i = 0; i < dimensions; i++) {
            coords[i] = coord;
        }
        return new Coordinate<K>(coords);
    }

    @Override
    public Coordinate<K> fromInt(int value) {
        return getAllSame(keyProto.fromInt(value));
    }

    @Override
    public KeyRange<Coordinate<K>> getEntireRange() {
        return new KeyRangeImpl<Coordinate<K>>(getMinKey(), getMaxKey());
    }

    @Override
    public Coordinate<K> getMaxKey() {
        return getAllSame(keyProto.getMaxKey());
    }

    @Override
    public Coordinate<K> getMinKey() {
        return getAllSame(keyProto.getMinKey());
    }

    /**
     * @param lowLimits the lower limits of each coordinate (null for no
     * limits)
     * @return the next key such that one of the coordinates is higher than in
     * this, and none are lower than the low limits
     */
    public Coordinate<K> nextKey(Coordinate<K> lowLimits, Coordinate<K> highLimits) {
        if (isPrototype)
            throw new UnsupportedOperationException("nextKey() not supported for prototype keys");
        assert dimensions == this.coordinates.length;

        K[] nCoords = createArray();
        System.arraycopy(this.coordinates, 0, nCoords, 0, dimensions);
        for (int i = dimensions - 1; i >= 0; i--) {
            K curKey = nCoords[i];
            K nextKey = curKey.nextKey();
            // Check that 1. key is changed and 2. new key is not above the
            // given high limits
            if (!curKey.equals(nextKey)
                && (highLimits == null || nextKey.compareTo(highLimits.get(i)) <= 0)) {
                nCoords[i] = nextKey;
                return new Coordinate<K>(nCoords);
            } else {
                // Swap this to lower limits
                nCoords[i] = lowLimits != null ? lowLimits.get(i) : keyProto.getMinKey();
            }
        }
        // All keys were at maximum, so cannot advance
        return this;
    }

    @Override
    public Coordinate<K> nextKey() {
        return nextKey(null, null);
    }

    /**
     * @param lowLimits the low limits of each coordinate (null for no limits)
     * @return the next key such that one of the coordinates is lower than in
     * this, and none are lower than the low limits
     */
    public Coordinate<K> previousKey(Coordinate<K> lowLimits, Coordinate<K> highLimits) {
        if (isPrototype)
            throw new UnsupportedOperationException("nextKey() not supported for prototype keys");
        assert dimensions == this.coordinates.length;
        K[] nCoords = createArray();
        System.arraycopy(this.coordinates, 0, nCoords, 0, dimensions);
        for (int i = dimensions - 1; i >= 0; i--) {
            K curKey = nCoords[i];
            K prevKey = curKey.previousKey();
            // Check that 1. key is changed and 2. new key is not below the
            // given low limits
            if (!curKey.equals(prevKey)
                && (lowLimits == null || prevKey.compareTo(lowLimits.get(i)) >= 0)) {
                nCoords[i] = prevKey;
                return new Coordinate<K>(nCoords);
            } else {
                // Swap to high limits
                nCoords[i] = highLimits != null ? highLimits.get(i) : keyProto.getMaxKey();
            }
        }
        // All keys were at maximum, so cannot advance
        return this;
    }

    @Override
    public Coordinate<K> previousKey() {
        return previousKey(null, null);
    }

    @Override
    public float toFloat() {
        return isPrototype ? keyProto.toFloat() : coordinates[0].toFloat();
    }

    @Override
    public int toInt() {
        return isPrototype ? keyProto.toInt() : coordinates[0].toInt();
    }

    @Override
    public int compareTo(Coordinate<K> c) {
        if (dimensions != c.dimensions)
            throw new UnsupportedOperationException("Dimension counts do not match: "
                + dimensions + " vs " + c.dimensions);

        if (isPrototype || c.isPrototype) {
            if (isPrototype && c.isPrototype)
                return 0;
            return isPrototype ? -1 : 1;
        }

        for (int i = 0; i < dimensions; i++) {
            int comp = coordinates[i].compareTo(c.coordinates[i]);
            if (comp != 0)
                return comp;
        }
        return 0;
    }

    @Override
    public boolean isValid(String value) {
        return parse(value) != null;
    }

    @Override
    public Coordinate<K> parse(String value) {
        K[] keys = KeyParser.parseKeys(keyProto, dimensions, value);
        return keys != null ? new Coordinate<K>(keys) : null;
    }

    @Override
    public int hashCode() {
        return isPrototype ? keyProto.hashCode() : coordinates[0].hashCode();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (!(o instanceof Coordinate))
            return false;

        Coordinate<K> c = (Coordinate<K>) o;
        if (dimensions != c.dimensions)
            throw new UnsupportedOperationException("Dimension counts do not match: "
                + dimensions + " vs " + c.dimensions);

        if (isPrototype || c.isPrototype)
            return isPrototype == c.isPrototype;

        for (int i = dimensions - 1; i >= 0; i--) {
            if (!coordinates[i].equals(c.coordinates[i]))
                return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return write(this);
    }

    @Override
    public String write(Coordinate<K> object) {
        return object.write();
    }

    @Override
    public String write() {
        StringBuilder buf = new StringBuilder();
        buf.append("(");
        for (int i = 0; i < dimensions; i++) {
            if (i != 0)
                buf.append(",");
            buf.append(coordinates[i].write());
        }
        buf.append(")");
        return buf.toString();
    }

    @Override
    public int getByteDataSize() {
        return keyProto.getByteDataSize() * dimensions;
    }

    @Override
    public Coordinate<K> readFromBytes(ByteBuffer byteArray) {
        K[] nCoords = createArray();
        for (int i = 0; i < dimensions; i++) {
            K coord = keyProto.readFromBytes(byteArray);
            nCoords[i] = coord;
        }
        return new Coordinate<K>(nCoords);
    }

    @Override
    public void writeToBytes(ByteBuffer byteArray) {
        for (int i = 0; i < dimensions; i++) {
            coordinates[i].writeToBytes(byteArray);
        }
    }

    @Override
    public boolean isPrototype() {
        return isPrototype;
    }

    @Override
    public Coordinate<K> abs() {
        throw new NotImplementedException();
    }

    @Override
    public Coordinate<K> subtract(Coordinate<K> other) {
        K[] c = createArray();
        for (int i = 0; i < dimensions; i++) {
            c[i] = coordinates[i].subtract(other.coordinates[i]);
        }
        return new Coordinate<K>(c);
    }

    @Override
    public Coordinate<K> add(Coordinate<K> other) {
        K[] c = createArray();
        for (int i = 0; i < dimensions; i++) {
            c[i] = coordinates[i].add(other.coordinates[i]);
        }
        return new Coordinate<K>(c);
    }

    @Override
    public Coordinate<K> divide(Coordinate<K> divider) {
        K[] c = createArray();
        for (int i = 0; i < dimensions; i++) {
            c[i] = coordinates[i].divide(divider.coordinates[i]);
        }
        return new Coordinate<K>(c);
    }

    @Override
    public Coordinate<K> multiply(Coordinate<K> multiplier) {
        K[] c = createArray();
        for (int i = 0; i < dimensions; i++) {
            c[i] = coordinates[i].multiply(multiplier.coordinates[i]);
        }
        return new Coordinate<K>(c);
    }

    @Override
    public Coordinate<K> multiply(double multiplier) {
        K[] c = createArray();
        for (int i = 0; i < dimensions; i++) {
            c[i] = coordinates[i].multiply(multiplier);
        }
        return new Coordinate<K>(c);
    }

    @Override
    public Coordinate<K> random(Coordinate<K> min, Coordinate<K> max, Random random) {
        K[] coords = createArray();
        assert coordinates[0] != null;
        for (int i = 0; i < dimensions; i++) {
            coords[i] = coordinates[0].random(min.get(i), max.get(i), random);
        }
        return new Coordinate<K>(coords);
    }

}
