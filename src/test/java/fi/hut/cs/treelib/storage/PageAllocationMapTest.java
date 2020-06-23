package fi.hut.cs.treelib.storage;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.PageID;

import junit.framework.TestCase;

public class PageAllocationMapTest extends TestCase {

    private static final Logger log = Logger.getLogger(PageAllocationMapTest.class);

    public void testValues() {
        byte[] dataBytes = new byte[100];
        ByteBuffer data = ByteBuffer.wrap(dataBytes);
        PageAllocationMap map = new PageAllocationMap(new PageID(0), 100);
        map.formatNewPage(data);

        assertEquals(0, map.getMinPageID().intValue());
        assertEquals(767, map.getMaxPageID().intValue());

        dataBytes = new byte[100];
        data = ByteBuffer.wrap(dataBytes);
        map = new PageAllocationMap(new PageID(800), 100);
        map.formatNewPage(data);

        assertEquals(800, map.getMinPageID().intValue());
        assertEquals(1567, map.getMaxPageID().intValue());
    }

    public void testIsFree() {
        byte[] dataBytes = new byte[100];
        ByteBuffer data = ByteBuffer.wrap(dataBytes);
        PageAllocationMap map = new PageAllocationMap(new PageID(0), 100);
        map.formatNewPage(data);

        assertFalse(map.isFree(new PageID(0)));
        for (int i = 1; i < 768; i++) {
            assertTrue(map.isFree(new PageID(i)));
        }

        map.reserve(new PageID(5));
        map.reserve(new PageID(10));
        assertFalse(map.isFree(new PageID(0)));
        assertTrue(map.isFree(new PageID(1)));
        assertTrue(map.isFree(new PageID(2)));
        assertTrue(map.isFree(new PageID(4)));
        assertFalse(map.isFree(new PageID(5)));
        assertTrue(map.isFree(new PageID(6)));
        assertTrue(map.isFree(new PageID(9)));
        assertFalse(map.isFree(new PageID(10)));
        assertTrue(map.isFree(new PageID(11)));

        log.debug(map);
    }

    public void testBytes() {
        byte[] dataBytes = new byte[100];
        ByteBuffer data = ByteBuffer.wrap(dataBytes);
        PageAllocationMap map = new PageAllocationMap(new PageID(0), 100);
        map.formatNewPage(data);

        int type = map.getTypeIdentifier();
        assertEquals((byte) ((type >> 24) & 0xff), dataBytes[0]);
        assertEquals((byte) ((type >> 16) & 0xff), dataBytes[1]);
        assertEquals((byte) ((type >> 8) & 0xff), dataBytes[2]);
        assertEquals((byte) (type & 0xff), dataBytes[3]);

        assertEquals(1, dataBytes[4]);
        for (int i = 1; i < 96; i++) {
            assertEquals(0, dataBytes[i + 4]);
        }

        map.reserve(new PageID(10));
        assertEquals(1, dataBytes[4]);

        map.reserve(new PageID(5));
        assertEquals(1 + (1 << 5), dataBytes[4]);

        assertEquals((1 << 2), dataBytes[5]);
    }

    public void testFindAndReserve() {
        byte[] dataBytes = new byte[100];
        ByteBuffer data = ByteBuffer.wrap(dataBytes);
        PageAllocationMap map = new PageAllocationMap(new PageID(0), 100);
        map.formatNewPage(data);

        map.reserve(new PageID(10));
        for (int i = 1; i < 10; i++) {
            assertEquals("Not " + i, i, map.findAndReserve().intValue());
        }
        // 10 skipped as it was already reserved
        for (int i = 11; i < 20; i++) {
            assertEquals("Not " + i, i, map.findAndReserve().intValue());
        }
        log.debug(map);
        log.debug(map.getAllocationStatus(new PageID(0), new PageID(20)));

    }

}
