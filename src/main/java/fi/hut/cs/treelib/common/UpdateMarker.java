package fi.hut.cs.treelib.common;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.PageValue;
import fi.tuska.util.Equalities;

public class UpdateMarker<V extends PageValue<?>> implements PageValue<V> {

    private static final String DELETE_PREFIX = "delete";
    private static final String INSERT_PREFIX = "insert:";
    private static final int DELETE_INT = Integer.MIN_VALUE;

    private enum Type {
        INSERT, DELETE
    }

    private final V value;
    private final Type type;

    public UpdateMarker(V value, Type type) {
        this.value = value;
        this.type = type;
    }

    public static <V extends PageValue<?>> UpdateMarker<V> createInsert(V value) {
        return new UpdateMarker<V>(value, Type.INSERT);
    }

    public static <V extends PageValue<?>> UpdateMarker<V> createDelete(V proto) {
        return new UpdateMarker<V>(proto, Type.DELETE);
    }

    /**
     * @return the value of the inserted item (for inserts); or null (for
     * deletions)
     */
    @Override
    public V getValue() {
        if (type == Type.DELETE)
            return null;

        return value;
    }

    public boolean isInsert() {
        return type == Type.INSERT;
    }

    public boolean isDelete() {
        return type == Type.DELETE;
    }

    @Override
    public String write() {
        return value.write();
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageValue<V> parse(String source) {
        if (source.startsWith(DELETE_PREFIX)) {
            return createDelete(value);
        } else if (source.startsWith(INSERT_PREFIX)) {
            String valStr = source.substring(INSERT_PREFIX.length());
            V val = (V) value.parse(valStr);
            return createInsert(val);
        } else {
            throw new UnsupportedOperationException("Unknown update type: " + source);
        }
    }

    @Override
    public int getByteDataSize() {
        return value.getByteDataSize();
    }

    @Override
    @SuppressWarnings("unchecked")
    public UpdateMarker<V> readFromBytes(ByteBuffer byteArray) {
        // Read in first integer without moving the pointer
        int firstInt = byteArray.getInt(byteArray.position());

        if (firstInt == DELETE_INT) {
            // Skip reading the value
            byteArray.position(byteArray.position() + value.getByteDataSize());
            return createDelete(value);
        } else {
            V val = (V) value.readFromBytes(byteArray);
            return createInsert(val);
        }
    }

    @Override
    public void writeToBytes(ByteBuffer byteArray) {
        if (type == Type.DELETE) {
            int tSize = getByteDataSize();
            byteArray.putInt(DELETE_INT);
            int rem = tSize - 4;
            if (rem > 0) {
                // Skip to the end of the data area
                byteArray.position(byteArray.position() + rem);
            }
            if (rem < 0) {
                throw new UnsupportedOperationException(
                    "Data size is too small (at least four required)");
            }
        } else {
            value.writeToBytes(byteArray);
        }
    }

    @Override
    public String toString() {
        if (type == Type.DELETE) {
            return "-";
        } else if (type == Type.INSERT) {
            return "+" + value;
        } else
            throw new IllegalStateException("Invalid type: " + type);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UpdateMarker))
            return false;
        UpdateMarker<V> m = (UpdateMarker<V>) o;
        return type.equals(m.type) && Equalities.equalsOrNull(value, m.value);
    }
}
