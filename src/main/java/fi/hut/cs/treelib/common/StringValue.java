package fi.hut.cs.treelib.common;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.util.ByteUtils;

public class StringValue implements PageValue<String> {

    public static final StringValue PROTOTYPE = new StringValue();
    private static final Charset ENCODING_CHARSET = Charset.forName("UTF-8");

    private final String value;
    /** The maximum amount of bytes available for storing the strings. */
    private final int maxLength;

    public StringValue(String value) {
        this.value = value;
        assert value != null;
        this.maxLength = Configuration.instance().getMaxStringLength();
    }

    public StringValue(Object value) {
        this(value.toString());
    }

    /** Creates the prototype value. */
    private StringValue() {
        this("");
    }

    public StringValue(int intValue) {
        this(String.valueOf(intValue));
    }

    public StringValue(long longValue) {
        this(String.valueOf(longValue));
    }

    @Override
    public String getValue() {
        return value;
    }

    /**
     * Writes the string to the storage. Always moves the byte buffer position
     * by the maximum length.
     */
    @Override
    public void writeToBytes(ByteBuffer arr) {
        int written = ByteUtils.writeString(arr, value, maxLength, ENCODING_CHARSET);
        if (written < maxLength) {
            // Pad to max length
            int remaining = maxLength - written;
            // Move the cursor
            arr.position(arr.position() + remaining);
        }
    }

    @Override
    public StringValue readFromBytes(ByteBuffer arr) {
        String val = ByteUtils.readString(arr, maxLength, ENCODING_CHARSET);
        return new StringValue(val);
    }

    @Override
    public String write() {
        return value;
    }

    public Charset getEncodingCharset() {
        return ENCODING_CHARSET;
    }

    @Override
    public int getByteDataSize() {
        return maxLength;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (o instanceof StringValue)
            return value.equals(((StringValue) o).value);
        if (o instanceof String)
            return value.equals(o);
        return false;
    }

    @Override
    public StringValue parse(String source) {
        return new StringValue(source);
    }

}
