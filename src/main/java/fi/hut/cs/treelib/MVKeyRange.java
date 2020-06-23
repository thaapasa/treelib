package fi.hut.cs.treelib;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerKeyRange;
import fi.hut.cs.treelib.internal.KeyRangeImpl;

/**
 * Models key ranges of the form [min, max) with version ranges [start, end).
 * The key range is immutable (like Strings), so you do not need to worry
 * about sharing references to the same key range.
 * 
 * @author thaapasa
 */
public class MVKeyRange<K extends Key<K>> extends KeyRangeImpl<K> {

    public static int MAX = Integer.MAX_VALUE;
    public static int MIN = Integer.MIN_VALUE;

    private final IntegerKeyRange versionRange;

    public MVKeyRange(K min, K max, int startVersion, int endVersion) {
        super(min, max);
        this.versionRange = new IntegerKeyRange(startVersion, endVersion);
    }

    public MVKeyRange(KeyRange<K> range, int startVersion, int endVersion) {
        super(range);
        this.versionRange = new IntegerKeyRange(startVersion, endVersion);
    }

    /**
     * Creates a MV key range that contains only a single key, with
     * versionRange startVersion -> endVersion
     */
    public MVKeyRange(K key, int startVersion, int endVersion) {
        super(key);
        this.versionRange = new IntegerKeyRange(startVersion, endVersion);
    }

    /**
     * Creates a MV key range for a single key range with infinite version
     * range
     */
    public MVKeyRange(K key, int startVersion) {
        super(key);
        this.versionRange = new IntegerKeyRange(startVersion, Integer.MAX_VALUE);
    }

    /**
     * Creates a MV key range that contains only a single key, with
     * versionRange startVersion -> endVersion
     */
    public MVKeyRange(K key, KeyRange<IntegerKey> versionRange) {
        super(key);
        this.versionRange = IntegerKeyRange.convert(versionRange);
    }

    public MVKeyRange(KeyRange<K> keyRange, KeyRange<IntegerKey> versionRange) {
        super(keyRange);
        this.versionRange = IntegerKeyRange.convert(versionRange);
    }

    public MVKeyRange(K min, K max, int startVersion) {
        this(min, max, startVersion, MAX);
    }

    public MVKeyRange(K min, K max, KeyRange<IntegerKey> versionRange) {
        super(min, max);
        this.versionRange = IntegerKeyRange.convert(versionRange);
    }

    @Override
    public boolean overlaps(KeyRange<K> o) {
        if (o instanceof MVKeyRange<?>) {
            MVKeyRange<K> k = (MVKeyRange<K>) o;
            return super.overlaps(k) && versionRange.overlaps(k.versionRange);
        }
        return super.overlaps(o);
    }

    @Override
    public MVKeyRange<K> getEntireKeyRange() {
        return new MVKeyRange<K>(super.getEntireKeyRange(), versionRange.getEntireKeyRange());
    }

    public IntegerKeyRange getVersionRange() {
        return versionRange;
    }

    public Integer getMinVersion() {
        return versionRange.getMin().intValue();
    }

    public Integer getMaxVersion() {
        return versionRange.getMax().intValue();
    }

    public boolean containsVersion(int version) {
        return versionRange.contains(version);
    }

    public MVKeyRange<K> extendVersion(int version) {
        if (versionRange.contains(new IntegerKey(version)))
            return this;
        return new MVKeyRange<K>(this, versionRange.extend(new IntegerKey(version)));
    }

    @Override
    public MVKeyRange<K> extend(K key) {
        if (contains(key))
            return this;
        return new MVKeyRange<K>(super.extend(key), getVersionRange());
    }

    /**
     * Extends this range to cover the given range also. The given range must
     * be adjacent to this range. The version range of the resulting
     * MultiVersionKeyRange will be taken from this MVKR.
     * 
     * @param range the range to cover
     * @return the new range consisting of the original range + the given
     * range
     */
    public MVKeyRange<K> extendKeyRange(KeyRange<K> other) {
        if (getMin().equals(other.getMax())) {
            // This range is just after the given range
            return new MVKeyRange<K>(other.getMin(), getMax(), getMinVersion(), getMaxVersion());
        }
        if (getMax().equals(other.getMin())) {
            // This range is just before the given range
            return new MVKeyRange<K>(getMin(), other.getMax(), getMinVersion(), getMaxVersion());
        }
        throw new IllegalArgumentException(String.format(
            "Given range %s is not adjacent to range %s", other, this));
    }

    /**
     * @param endVersion the new end version
     * @return the new MVB range with version range terminated at endVersion
     */
    public MVKeyRange<K> endVersionRange(int endVersion) {
        if (versionRange.getMax().intValue() == endVersion)
            return this;
        if (getMinVersion().intValue() == endVersion)
            throw new IllegalArgumentException("Trying to create an empty version range: "
                + endVersion);
        return new MVKeyRange<K>(this, getMinVersion(), endVersion);
    }

    /**
     * @param startVersion the start end version
     * @return the new MVB range with version range starting at startVersion
     */
    public MVKeyRange<K> startVersionRange(int startVersion) {
        if (versionRange.getMin().intValue() == startVersion)
            return this;

        if (getMaxVersion().intValue() == startVersion)
            throw new IllegalArgumentException("Trying to create an empty version range: "
                + startVersion);

        return new MVKeyRange<K>(this, startVersion, getMaxVersion());
    }

    /**
     * @return true if the given key is contained in this range
     */
    public boolean contains(K key, int version) {
        // First check the version
        if (!versionRange.contains(new IntegerKey(version))) {
            // Not alive at this version anyway
            return false;
        }
        // Version is ok, check for key match
        return contains(key);
    }

    @Override
    public int compareTo(KeyRange<K> o) {
        if (o == null || !(o instanceof MVKeyRange<?>)) {
            return -1;
        }
        try {
            MVKeyRange<K> m = (MVKeyRange<K>) o;

            int c = 0;
            // First compare by min key of key range
            c = getMin().compareTo(m.getMin());
            if (c != 0)
                return c;
            // Then compare by min key of version range
            c = versionRange.getMin().compareTo(m.versionRange.getMin());
            if (c != 0)
                return c;
            // Then compare by max key of key range
            c = getMax().compareTo(m.getMax());
            if (c != 0)
                return c;
            // Then compare by max key of version range
            c = versionRange.getMax().compareTo(m.versionRange.getMax());
            if (c != 0)
                return c;
            // All keys are same - return 0
            return 0;
        } catch (ClassCastException e) {
            return -1;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (!(o instanceof MVKeyRange)) {
            return false;
        }
        try {
            MVKeyRange<K> range = (MVKeyRange<K>) o;
            return super.equals(range) && versionRange.equals(range.versionRange);
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("%s, %s", super.toString(), versionRange.toString());
    }

    @Override
    public int getByteDataSize() {
        return super.getByteDataSize() + versionRange.getByteDataSize();
    }

    @Override
    public MVKeyRange<K> readFromBytes(ByteBuffer byteArray) {
        KeyRange<K> keyRange = super.readFromBytes(byteArray);
        KeyRange<IntegerKey> vRange = versionRange.readFromBytes(byteArray);

        return new MVKeyRange<K>(keyRange, vRange);
    }

    @Override
    public void writeToBytes(ByteBuffer byteArray) {
        super.writeToBytes(byteArray);
        versionRange.writeToBytes(byteArray);
    }

}
