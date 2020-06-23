package fi.hut.cs.treelib.common;

import java.nio.ByteBuffer;

import fi.tuska.util.StringParser;

/**
 * An abstract class for storing a single integer value. Handles the basic
 * stuff such as value comparison, parsing the value from a string and
 * writing/reading from byte buffers.
 * 
 * @author thaapasa
 */
public abstract class AbstractIntegerValue<V extends AbstractIntegerValue<V>> implements
    Comparable<V>, StringParser<V> {

    private static final int STORED_NULL_VALUE = -1;
    protected final Integer intValue;

    protected AbstractIntegerValue(Integer value) {
        this.intValue = value;
    }

    public int intValue() {
        return intValue != null ? intValue : 0;
    }

    public boolean isValid() {
        return intValue != null;
    }

    protected abstract V instantiate(Integer value);

    public V fromInt(int value) {
        return instantiate(value);
    }

    public int getByteDataSize() {
        // Integer size is 4 bytes
        return Integer.SIZE / 8;
    }

    public void writeToBytes(ByteBuffer data) {
        data.putInt(intValue != null ? intValue.intValue() : STORED_NULL_VALUE);
    }

    public V readFromBytes(ByteBuffer data) {
        int intValue = data.getInt();
        return instantiate(intValue != STORED_NULL_VALUE ? intValue : null);
    }

    public float toFloat() {
        return intValue != null ? (float) intValue.intValue() : 0;
    }

    public int toInt() {
        return intValue != null ? intValue.intValue() : 0;
    }

    @Override
    public V parse(String value) {
        if (!INTEGER_PARSER.isValid(value))
            return instantiate(0);
        Integer val = INTEGER_PARSER.parse(value);
        return instantiate(val);
    }

    @Override
    public boolean isValid(String value) {
        return INTEGER_PARSER.isValid(value);
    }

    @Override
    public String write(V object) {
        return object.write();
    }

    public String write() {
        if (intValue == null)
            return "null";
        return INTEGER_PARSER.write(intValue);
    }

    @Override
    public int hashCode() {
        return intValue != null ? intValue.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (!(o instanceof AbstractIntegerValue<?>))
            return false;
        return abstractCompareTo((AbstractIntegerValue<?>) o) == 0;
    }

    @Override
    public int compareTo(V o) {
        return abstractCompareTo(o);
    }

    private int abstractCompareTo(AbstractIntegerValue<?> o) {
        if (o == null)
            return 1;
        if (o.intValue == null)
            return intValue == null ? 0 : 1;
        if (intValue == null)
            return -1;

        return intValue.compareTo(o.intValue);
    }

    @Override
    public String toString() {
        return intValue != null ? intValue.toString() : "null";
    }

}
