package fi.hut.cs.treelib;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.common.IntegerKey;

public class MBRTest extends TreeLibTest {

    private static final Logger log = Logger.getLogger(MBRTest.class);

    private static final IntegerKey K1 = new IntegerKey(1);
    private static final IntegerKey K2 = new IntegerKey(2);
    private static final IntegerKey K3 = new IntegerKey(3);
    private static final IntegerKey K4 = new IntegerKey(4);
    private static final IntegerKey K5 = new IntegerKey(5);
    private static final IntegerKey K6 = new IntegerKey(6);

    public void testDimensions() {
        MBR<IntegerKey> mbr = new MBR<IntegerKey>(K1, K2, K3, K4, K5, K6);
        log.info(mbr);
        assertEquals(3, mbr.getDimensions());
        assertEquals(K1, mbr.getLow(0));
        assertEquals(K3, mbr.getLow(1));
        assertEquals(K5, mbr.getLow(2));
        assertEquals(K2, mbr.getHigh(0));
        assertEquals(K4, mbr.getHigh(1));
        assertEquals(K6, mbr.getHigh(2));

        mbr = new MBR<IntegerKey>(K6, K3);
        log.info(mbr);
        assertEquals(1, mbr.getDimensions());
        assertEquals(K3, mbr.getLow(0));
        assertEquals(K6, mbr.getHigh(0));

        try {
            mbr.getHigh(1);
            fail("getHigh(1) did not throw ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // OK
        }
    }

    public void testIntersection() {
        MBR<IntegerKey> proto = new MBR<IntegerKey>(2, IntegerKey.PROTOTYPE, false);
        MBR<IntegerKey> mbr1 = proto.parse("(1,2):(5,5)");
        MBR<IntegerKey> mbr2 = proto.parse("(3,3):(6,8)");
        MBR<IntegerKey> mbri = mbr1.intersection(mbr2);
        assertNotNull(mbri);
        assertFalse(mbri.isEmpty());
        assertEquals(4, mbri.getArea().toInt());
        assertEquals(3, mbri.getMin().get(0).intValue());
        assertEquals(3, mbri.getMin().get(1).intValue());
        assertEquals(5, mbri.getMax().get(0).intValue());
        assertEquals(5, mbri.getMax().get(1).intValue());

        MBR<IntegerKey> mbr3 = proto.parse("(1,1):(2,3)");
        mbri = mbr1.intersection(mbr3);
        assertNotNull(mbri);
        assertFalse(mbri.isEmpty());
        assertEquals(1, mbri.getArea().toInt());
        assertEquals(1, mbri.getMin().get(0).intValue());
        assertEquals(2, mbri.getMin().get(1).intValue());
        assertEquals(2, mbri.getMax().get(0).intValue());
        assertEquals(3, mbri.getMax().get(1).intValue());

        MBR<IntegerKey> mbr4 = proto.parse("(5,1):(7,4)");
        mbri = mbr1.intersection(mbr4);
        assertNotNull(mbri);
        assertFalse(mbri.isEmpty());
        assertEquals(0, mbri.getArea().toInt());
        assertEquals(5, mbri.getMin().get(0).intValue());
        assertEquals(2, mbri.getMin().get(1).intValue());
        assertEquals(5, mbri.getMax().get(0).intValue());
        assertEquals(4, mbri.getMax().get(1).intValue());

        MBR<IntegerKey> mbr5 = proto.parse("(5,5):(7,7)");
        mbri = mbr1.intersection(mbr5);
        assertNotNull(mbri);
        assertTrue(mbri.isEmpty());
        assertEquals(0, mbri.getArea().toInt());
        assertEquals(5, mbri.getMin().get(0).intValue());
        assertEquals(5, mbri.getMin().get(1).intValue());
        assertEquals(5, mbri.getMax().get(0).intValue());
        assertEquals(5, mbri.getMax().get(1).intValue());

        MBR<IntegerKey> mbr6 = proto.parse("(8,3):(10,5)");
        mbri = mbr1.intersection(mbr6);
        assertNull(mbri);
    }

    public void testPerimeter() {
        MBR<IntegerKey> proto = new MBR<IntegerKey>(1, IntegerKey.PROTOTYPE, true);
        MBR<IntegerKey> mbr = proto.parse("1, 5");
        assertEquals(4, mbr.getPerimeter().toInt());

        proto = new MBR<IntegerKey>(2, IntegerKey.PROTOTYPE, true);
        mbr = proto.parse("1, 5, 7, 10");
        assertEquals(2 * 4 + 2 * 3, mbr.getPerimeter().toInt());

        proto = new MBR<IntegerKey>(3, IntegerKey.PROTOTYPE, true);
        mbr = proto.parse("1, 5, 7, 10, 4, 6");
        assertEquals(4 * 4 + 4 * 3 + 4 * 2, mbr.getPerimeter().toInt());
    }

    public void testArea() {
        MBR<IntegerKey> proto = new MBR<IntegerKey>(2, IntegerKey.PROTOTYPE, true);
        MBR<IntegerKey> mbr = proto.parse("1, 5, 3, 5");
        assertEquals(1, mbr.getMin().get(0).intValue());
        assertEquals(5, mbr.getMax().get(0).intValue());
        assertAboutSame(4, mbr.getDistance(0).toInt());
        assertAboutSame(2, mbr.getDistance(1).toInt());
        assertAboutSame(8, mbr.getArea().toInt());

        proto = new MBR<IntegerKey>(3, IntegerKey.PROTOTYPE, true);
        mbr = proto.parse("0, 10, 10, 15, 2, 4");
        assertAboutSame(10, mbr.getDistance(0).toInt());
        assertAboutSame(5, mbr.getDistance(1).toInt());
        assertAboutSame(2, mbr.getDistance(2).toInt());
        assertAboutSame(100, mbr.getArea().toInt());
    }

    public void testOverlapArea() {
        MBR<IntegerKey> proto = new MBR<IntegerKey>(2, IntegerKey.PROTOTYPE, true);
        MBR<IntegerKey> mbr = proto.parse("1, 3, 1, 4");
        assertAboutSame(1, mbr.countOverlapArea(proto.parse("2 6 3 6")).toInt());
        assertAboutSame(0, mbr.countOverlapArea(proto.parse("3 6 3 6")).toInt());
        assertAboutSame(0, mbr.countOverlapArea(proto.parse("4 6 3 6")).toInt());
        assertAboutSame(4, mbr.countOverlapArea(proto.parse("1 4 0 3")).toInt());
    }

    public void testByteConversions() {
        MBR<IntegerKey> mbr = new MBR<IntegerKey>(K1, K2, K3, K4, K5, K6);
        assertEquals(6 * 4, mbr.getByteDataSize());

        byte[] arr = new byte[mbr.getByteDataSize()];
        ByteBuffer buf = ByteBuffer.wrap(arr);
        mbr.writeToBytes(buf);
        assertEquals(mbr.getByteDataSize(), buf.position());

        MBR<IntegerKey> proto = new MBR<IntegerKey>(3, IntegerKey.PROTOTYPE, true);
        assertEquals(3, proto.getDimensions());

        buf.rewind();
        MBR<IntegerKey> read = proto.readFromBytes(buf);
        assertEquals(K1, read.getLow(0));
        assertEquals(K2, read.getHigh(0));
        assertEquals(K3, read.getLow(1));
        assertEquals(K4, read.getHigh(1));
        assertEquals(K5, read.getLow(2));
        assertEquals(K6, read.getHigh(2));
    }

    public void testParse() {
        MBR<IntegerKey> proto1 = new MBR<IntegerKey>(1, IntegerKey.PROTOTYPE, true);
        MBR<IntegerKey> proto2 = new MBR<IntegerKey>(2, IntegerKey.PROTOTYPE, true);
        assertTrue(proto1.isValid("1 2"));
        assertTrue(proto1.isValid("(1    2)"));
        assertTrue(proto2.isValid("1 ((2<>4<5"));
        assertFalse(proto1.isValid("1"));
        assertFalse(proto1.isValid(""));
        assertFalse(proto1.isValid("()<<"));
        assertFalse(proto1.isValid("1"));
        assertFalse(proto2.isValid("2 3 4"));

        String pat = "1 2 3 4";
        assertTrue(proto2.isValid(pat));
        MBR<IntegerKey> mbr = proto2.parse(pat);
        assertEquals(K1, mbr.getLow(0));
        assertEquals(K2, mbr.getHigh(0));
        assertEquals(K3, mbr.getLow(1));
        assertEquals(K4, mbr.getHigh(1));

        pat = "<(5,6),(2,4)>";
        assertTrue(proto2.isValid(pat));
        mbr = proto2.parse(pat);
        assertEquals(K5, mbr.getLow(0));
        assertEquals(K6, mbr.getHigh(0));
        assertEquals(K2, mbr.getLow(1));
        assertEquals(K4, mbr.getHigh(1));

        pat = "{(5,6),(2,4)}";
        assertTrue(proto2.isValid(pat));
        mbr = proto2.parse(pat);
        assertEquals(K5, mbr.getLow(0));
        assertEquals(K6, mbr.getHigh(0));
        assertEquals(K2, mbr.getLow(1));
        assertEquals(K4, mbr.getHigh(1));
    }

    public void testParse2() {
        MBR<IntegerKey> proto2 = new MBR<IntegerKey>(2, IntegerKey.PROTOTYPE, false);
        assertEquals(getMBR(1, 2, 10, 13), proto2.parse("1 10 2 13"));

        assertEquals("{(1:2),(10:13),(-3:7)}", getMBR(1, 2, 10, 13, -3, 7).toString());

        MBR<IntegerKey> proto3 = new MBR<IntegerKey>(3, IntegerKey.PROTOTYPE, false);
        assertEquals(getMBR(1, 2, 10, 13, -3, 7), proto3.parse("1 10 -3 2 13 7"));
        assertEquals("{(1,10,-3):(2,13,7)}", proto3.parse("1 10 -3 2 13 7").toString());
    }

    /** In order: x1, x2, y1, y2, ... */
    private MBR<IntegerKey> getMBR(int... coords) {
        IntegerKey[] c = new IntegerKey[coords.length];
        for (int i = 0; i < c.length; i++)
            c[i] = new IntegerKey(coords[i]);
        return new MBR<IntegerKey>(c);
    }

    public void testContains() {
        assertTrue(getMBR(1, 5, 3, 8).contains(getMBR(2, 4, 6, 7)));
        assertTrue(getMBR(1, 5, 3, 8).contains(getMBR(1, 5, 3, 3)));
        assertFalse(getMBR(1, 5, 3, 8).contains(getMBR(0, 5, 3, 3)));
        assertFalse(getMBR(1, 5, 3, 8).contains(getMBR(1, 5, 2, 3)));
        assertFalse(getMBR(1, 5, 3, 8).contains(getMBR(1, 5, 0, 1)));
    }

    public void testExtend() {
        MBR<IntegerKey> SRC = getMBR(2, 4, 3, 5);
        assertEquals(getMBR(1, 4, 3, 5), SRC.extend(getMBR(1, 3, 3, 4)));
        assertEquals(SRC, SRC.extend(getMBR(2, 3, 3, 4)));
        assertEquals(getMBR(2, 4, 3, 10), SRC.extend(getMBR(2, 3, 4, 10)));
        assertEquals(getMBR(-10, 4, 1, 5), SRC.extend(getMBR(-10, 3, 1, 4)));
    }

    private final int MAX = Integer.MAX_VALUE;
    private final int MIN = Integer.MIN_VALUE;

    public void testPreviousKey() {
        MBR<IntegerKey> src = getMBR(1, 5, 10, 11);
        assertBefore(src.previousKey(), src);
        src = src.previousKey();
        assertEquals(getMBR(1, 5, 10, 10), src);
        assertBefore(src.previousKey(), src);

        src = src.previousKey();
        assertEquals(getMBR(1, 4, 10, MAX), src);
        assertBefore(src.previousKey(), src);

        src = getMBR(1, 1, 10, 10);
        assertBefore(src.previousKey(), src);
        src = src.previousKey();
        assertEquals(getMBR(1, MAX, 9, MAX), src);
        assertBefore(src.previousKey(), src);

        src = getMBR(MIN, MIN, MIN, MIN);
        src = src.previousKey();
        assertEquals(getMBR(MIN, MIN, MIN, MIN), src);
    }

    public void testNextKey() {
        MBR<IntegerKey> src = getMBR(1, 5, 10, 11);
        assertBefore(src, src.nextKey());
        assertEquals(getMBR(1, 5, 10, 12), src.nextKey());

        src = getMBR(1, 5, 10, 10);
        assertBefore(src, src.nextKey());
        assertEquals(getMBR(1, 5, 10, 11), src.nextKey());

        src = getMBR(1, 5, 10, MAX);
        assertBefore(src, src.nextKey());
        assertEquals(getMBR(1, 6, 10, 10), src.nextKey());

        src = getMBR(2, MAX, 3, MAX);
        assertBefore(src, src.nextKey());
        src = src.nextKey();
        assertEquals(getMBR(2, 2, 4, 4), src);
        assertBefore(src, src.nextKey());
        src = src.nextKey();
        assertEquals(getMBR(2, 2, 4, 5), src);
        assertBefore(src, src.nextKey());
        src = src.nextKey();
        assertEquals(getMBR(2, 2, 4, 6), src);
        assertBefore(src, src.nextKey());

        src = getMBR(2, MAX, MAX, MAX);
        assertBefore(src, src.nextKey());
        src = src.nextKey();
        assertEquals(getMBR(3, 3, MIN, MIN), src);
        assertBefore(src, src.nextKey());
        src = src.nextKey();
        assertEquals(getMBR(3, 3, MIN, MIN + 1), src);
        assertBefore(src, src.nextKey());

        src = getMBR(MAX, MAX, MAX, MAX);
        src = src.nextKey();
        assertEquals(getMBR(MAX, MAX, MAX, MAX), src);
    }
}
