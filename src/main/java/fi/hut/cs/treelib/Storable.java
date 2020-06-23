package fi.hut.cs.treelib;

import java.nio.ByteBuffer;

/**
 * All objects that implement this interface can be stored to and loaded from
 * a byte array.
 * 
 * @author thaapasa
 */
public interface Storable<K> {

    void writeToBytes(ByteBuffer byteArray);

    /**
     * Creates a new instance of this class, and reads the required data from
     * the given byte array.
     * 
     * @return the newly created object
     */
    K readFromBytes(ByteBuffer byteArray);

    /**
     * @return the storage size required (in bytes) for storing this value
     */
    int getByteDataSize();

}
