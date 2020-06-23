package fi.hut.cs.treelib.util;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.apache.log4j.Logger;

public class ByteUtils {

    private static final Logger log = Logger.getLogger(ByteUtils.class);

    private ByteUtils() {
        // Private constructor to prevent instantiation
    }

    public static int writeInteger(byte[] arr, int offset, int value) {
        ByteBuffer buf = ByteBuffer.wrap(arr);
        // Write the integer, highest bytes first
        buf.putInt(offset, value);
        return 4;
    }

    public static int readInteger(byte[] arr, int offset) {
        ByteBuffer buf = ByteBuffer.wrap(arr);
        return buf.getInt(offset);
    }

    public static int writeLong(byte[] arr, int offset, long value) {
        ByteBuffer buf = ByteBuffer.wrap(arr);
        buf.putLong(offset, value);
        return 8;
    }

    public static long readLong(byte[] arr, int offset) {
        ByteBuffer buf = ByteBuffer.wrap(arr);
        return buf.getLong(offset);
    }

    public static int writeBytes(byte[] arr, int offset, byte[] bytes) {
        return writeBytes(arr, offset, bytes, bytes.length);
    }

    public static int writeBytes(byte[] arr, int offset, byte[] bytes, int length) {
        length = Math.min(length, bytes.length);
        ByteBuffer buf = ByteBuffer.wrap(arr, offset, length);
        buf.put(bytes);
        return length;
    }

    public static byte[] readBytes(byte[] arr, int offset, int amount) {
        ByteBuffer buf = ByteBuffer.wrap(arr, offset, amount);
        byte[] result = new byte[amount];
        buf.get(result);
        return result;
    }

    public static String readString(ByteBuffer data, int maxLen, Charset charset) {
        int strLen = data.getInt();
        int maxStrLen = maxLen - 4;
        if (strLen > maxStrLen) {
            log.error(String.format(
                "Too long string stored, reported len is %d, but max len is %d (%d)", strLen,
                maxLen, maxStrLen));
            return new String(data.array(), data.position(), maxStrLen, charset);
        } else {
            return new String(data.array(), data.position(), strLen, charset);
        }
    }

    /**
     * @param maxLen the maximum amount of bytes available for storing the
     * string. The maximum length for the actual stored string is four bytes
     * lower.
     * @return the amount of bytes written (including the four bytes for the
     * string length). This will be <= maxLen.
     */
    public static int writeString(ByteBuffer data, String str, int maxLen, Charset charset) {
        byte[] encoded = str.getBytes(charset);
        int written = 0;
        // 4 bytes for string length
        int maxStrLen = maxLen - 4;
        if (encoded.length > maxStrLen) {
            log.warn(String.format("Too long string, cropping len from %d to %d (max %d)",
                encoded.length, maxStrLen, maxLen));
            // Write string length
            data.putInt(maxStrLen);
            written += 4;
            // Write string
            data.put(encoded, 0, maxStrLen);
            written += maxStrLen;
        } else {
            // Write string length
            data.putInt(encoded.length);
            written += 4;
            // Write string
            data.put(encoded);
            written += encoded.length;
        }
        return written;
    }

    public static void setBit(ByteBuffer data, int bitNum) {
        int pos = bitNum / 8;
        int byt = bitNum % 8;
        if (pos >= data.remaining())
            throw new IndexOutOfBoundsException(bitNum + " outside array of len "
                + data.remaining());
        assert pos >= 0 : pos;
        assert byt < 8 && byt >= 0 : byt;
        int mask = 1 << byt;
        assert mask > 0 && mask <= 128 : mask;
        int value = data.get(pos) & 0xff;
        value |= mask;
        data.put(pos, (byte) value);
    }

    public static void clearBit(ByteBuffer data, int bitNum) {
        int pos = bitNum / 8;
        int byt = bitNum % 8;
        if (pos >= data.remaining())
            throw new IndexOutOfBoundsException(bitNum + " outside array of len "
                + data.remaining());
        assert pos >= 0 : pos;
        assert byt < 8 && byt >= 0 : byt;
        int mask = 1 << byt;
        assert mask > 0 && mask <= 128 : mask;
        data.put(pos, (byte) ((data.get(pos) & 0xff) & (~mask)));
    }

    public static boolean getBit(ByteBuffer data, int bitNum) {
        int pos = bitNum / 8;
        int byt = bitNum % 8;
        if (pos >= data.remaining())
            throw new IndexOutOfBoundsException(bitNum + " outside array of len "
                + data.remaining());
        assert pos >= 0 : pos;
        assert byt < 8 && byt >= 0 : byt;
        int mask = 1 << byt;
        assert mask > 0 && mask <= 128 : mask;
        return ((data.get(pos) & 0xff) & mask) > 0;
    }

}
