package fi.hut.cs.treelib;

import fi.hut.cs.treelib.common.IntegerKey;

public class CoordinateTest extends TreeLibTest {

    private IntegerKey getK(int i) {
        return new IntegerKey(i);
    }

    private Coordinate<IntegerKey> getC(int... coords) {
        IntegerKey[] ar = new IntegerKey[coords.length];
        for (int i = 0; i < coords.length; i++) {
            ar[i] = new IntegerKey(coords[i]);
        }
        return new Coordinate<IntegerKey>(ar);
    }

    public void testCreate() {
        assertFalse(IntegerKey.PROTOTYPE.isValid());

        Coordinate<IntegerKey> c = getC(1, 6, 2);
        assertEquals(3, c.getDimensions());
        assertTrue(c.isValid());

        assertEquals(getK(1), c.get(0));
        assertEquals(getK(6), c.get(1));
        assertEquals(getK(2), c.get(2));
        assertTrue(c.get(0).isValid());
        assertTrue(c.get(1).isValid());
        assertTrue(c.get(2).isValid());

        c = new Coordinate<IntegerKey>(2, IntegerKey.PROTOTYPE);

        assertEquals(2, c.getDimensions());
        assertFalse(c.isValid());
        assertFalse(c.get(0).isValid());
    }

    public void testNextKey() {
        Coordinate<IntegerKey> c = getC(5);
        Coordinate<IntegerKey> d = c.nextKey();
        assertEquals(getC(6), d);
        assertBefore(c, d);

        c = getC(1, 6, 2);
        d = c.nextKey();
        assertEquals(getC(1, 6, 3), d);
        assertBefore(c, d);

        c = getC(1, 6, Integer.MAX_VALUE);
        d = c.nextKey();
        assertEquals(getC(1, 7, Integer.MIN_VALUE), d);
        assertBefore(c, d);

        c = getC(1, Integer.MAX_VALUE, -4);
        d = c.nextKey();
        assertEquals(getC(1, Integer.MAX_VALUE, -3), d);
        assertBefore(c, d);

        c = getC(1, Integer.MAX_VALUE, Integer.MAX_VALUE);
        d = c.nextKey();
        assertEquals(getC(2, Integer.MIN_VALUE, Integer.MIN_VALUE), d);
        assertBefore(c, d);

        c = getC(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        d = c.nextKey();
        assertEquals(getC(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), d);
    }

    public void testPreviousKey() {
        Coordinate<IntegerKey> c = getC(5);
        Coordinate<IntegerKey> d = c.previousKey();
        assertEquals(getC(4), d);
        assertBefore(d, c);

        c = getC(1, 6, 2);
        d = c.previousKey();
        assertEquals(getC(1, 6, 1), d);
        assertBefore(d, c);

        c = getC(1, 6, Integer.MIN_VALUE);
        d = c.previousKey();
        assertEquals(getC(1, 5, Integer.MAX_VALUE), d);
        assertBefore(d, c);

        c = getC(1, Integer.MIN_VALUE, -4);
        d = c.previousKey();
        assertEquals(getC(1, Integer.MIN_VALUE, -5), d);
        assertBefore(d, c);

        c = getC(1, Integer.MIN_VALUE, Integer.MIN_VALUE);
        d = c.previousKey();
        assertEquals(getC(0, Integer.MAX_VALUE, Integer.MAX_VALUE), d);
        assertBefore(d, c);

        c = getC(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        d = c.previousKey();
        assertEquals(getC(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE), d);

        c = getC(5, 5);
        d = c.previousKey();
        assertEquals(getC(5, 4), d);
        assertBefore(d, c);
    }
}
