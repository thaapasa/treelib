package fi.hut.cs.treelib.common;

import java.nio.ByteBuffer;
import java.util.Random;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.internal.KeyRangeImpl;

/**
 * A versioned key that contains both the data key and a version number.
 * 
 * @author thaapasa
 */
public class VersionedKey<K extends Key<K>> implements Key<VersionedKey<K>> {

    private final int version;
    private final K key;

    public VersionedKey(K key, int version) {
        assert key != null;

        this.key = key;
        this.version = version;
    }

    public K getKey() {
        return key;
    }

    public int getVersion() {
        return version;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (o instanceof VersionedKey) {
            VersionedKey<K> k = (VersionedKey<K>) o;
            return key.equals(k.key) && version == k.version;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return key.hashCode() + version;
    }

    @Override
    public int compareTo(VersionedKey<K> other) {
        // First compare by keys
        int c = key.compareTo(other.key);
        if (c != 0)
            return c;

        // Then compare by version number
        return new Integer(version).compareTo(other.version);
    }

    @Override
    public String toString() {
        String vStr = null;
        if (version == Integer.MIN_VALUE)
            vStr = IntegerKey.MIN_KEY_STR;
        else if (version == Integer.MAX_VALUE)
            vStr = IntegerKey.MAX_KEY_STR;
        else
            vStr = String.valueOf(version);

        return String.format("%s@%s", key.toString(), vStr);
    }

    @Override
    public String write() {
        String vStr = null;
        if (version == Integer.MIN_VALUE)
            vStr = MIN_LOG_STR;
        else if (version == Integer.MAX_VALUE)
            vStr = MAX_LOG_STR;
        else
            vStr = String.valueOf(version);

        return String.format("%s@%s", key.write(), vStr);
    }

    /**
     * Format: [key]@[version], for example: 5@1
     */
    @Override
    public VersionedKey<K> parse(String source) {
        String[] params = source.split("@");
        if (params.length != 2) {
            throw new IllegalArgumentException("Source string is in invalid format: " + source);
        }
        return new VersionedKey<K>(key.parse(params[0].trim()), Integer
            .parseInt(params[1].trim()));
    }

    @Override
    public boolean isValid(String value) {
        try {
            parse(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public VersionedKey<K> fromInt(int value) {
        return new VersionedKey<K>(key.parse(String.valueOf(value)), 1);
    }

    protected VersionedKey<K> instantiate(K value) {
        return new VersionedKey<K>(value, version);
    }

    @Override
    public VersionedKey<K> abs() {
        return new VersionedKey<K>(key.abs(), version);
    }

    @Override
    public VersionedKey<K> add(VersionedKey<K> other) {
        return new VersionedKey<K>(key.add(other.key), version);
    }

    @Override
    public VersionedKey<K> divide(VersionedKey<K> divider) {
        return new VersionedKey<K>(key.divide(divider.key), version);
    }

    @Override
    public KeyRange<VersionedKey<K>> getEntireRange() {
        return new KeyRangeImpl<VersionedKey<K>>(getMinKey(), getMaxKey());
    }

    @Override
    public VersionedKey<K> getMaxKey() {
        return new VersionedKey<K>(key.getMaxKey(), Integer.MAX_VALUE);
    }

    @Override
    public VersionedKey<K> getMinKey() {
        return new VersionedKey<K>(key.getMinKey(), Integer.MIN_VALUE);
    }

    @Override
    public boolean isPrototype() {
        return key.isPrototype();
    }

    @Override
    public VersionedKey<K> multiply(VersionedKey<K> multiplier) {
        return new VersionedKey<K>(key.multiply(multiplier.key), version);
    }

    @Override
    public VersionedKey<K> multiply(double multiplier) {
        return new VersionedKey<K>(key.multiply(multiplier), version);
    }

    @Override
    public VersionedKey<K> nextKey() {
        // Staying in the same version, change if necessary
        return new VersionedKey<K>(key.nextKey(), version);
    }

    @Override
    public VersionedKey<K> previousKey() {
        // Staying in the same version, change if necessary
        return new VersionedKey<K>(key.previousKey(), version);
    }

    @Override
    public VersionedKey<K> random(VersionedKey<K> min, VersionedKey<K> max, Random random) {
        return new VersionedKey<K>(key.random(min.key, max.key, random), version);
    }

    @Override
    public VersionedKey<K> subtract(VersionedKey<K> other) {
        return new VersionedKey<K>(key.subtract(other.key), version);
    }

    @Override
    public float toFloat() {
        return key.toFloat();
    }

    @Override
    public int toInt() {
        return key.toInt();
    }

    @Override
    public String write(VersionedKey<K> object) {
        return object.write();
    }

    @Override
    public int getByteDataSize() {
        // Key + version (4 bytes)
        return key.getByteDataSize() + 4;
    }

    @Override
    public VersionedKey<K> readFromBytes(ByteBuffer byteArray) {
        // Read key
        K newKey = key.readFromBytes(byteArray);
        // Read version
        int newVer = byteArray.getInt();
        return new VersionedKey<K>(newKey, newVer);
    }

    @Override
    public void writeToBytes(ByteBuffer byteArray) {
        // Write key
        key.writeToBytes(byteArray);
        // Write version (4 bytes)
        byteArray.putInt(version);
    }

}
