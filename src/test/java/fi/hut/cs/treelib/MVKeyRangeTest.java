package fi.hut.cs.treelib;

import fi.hut.cs.treelib.common.IntegerKey;
import junit.framework.TestCase;

public class MVKeyRangeTest extends TestCase {

    protected static final IntegerKey proto = IntegerKey.PROTOTYPE;

    public void testRangeContains() {
        MVKeyRange<IntegerKey> range = new MVKeyRange<IntegerKey>(new IntegerKey(1),
            new IntegerKey(5), 1, 5);
        for (int version = 1; version < 5; version++) {
            for (int key = 1; key < 5; key++) {
                assertTrue(range.contains(new IntegerKey(key), version));
            }
        }
        assertFalse(range.contains(new IntegerKey(0), 1));
        assertFalse(range.contains(new IntegerKey(0), 3));
        assertFalse(range.contains(new IntegerKey(1), 0));
        assertFalse(range.contains(new IntegerKey(2), 5));
        assertFalse(range.contains(new IntegerKey(2), 6));

        assertFalse(new MVKeyRange<IntegerKey>(proto.getMinKey(), new IntegerKey(10), 1, 3)
            .contains(new IntegerKey(3), 3));
    }

    public void testRangePrint() {
        assertEquals("[1,5), [2," + IntegerKey.MAX_KEY_STR + ")", new MVKeyRange<IntegerKey>(
            new IntegerKey(1), new IntegerKey(5), 2).toString());
        assertEquals("[1,5), [2,7)", new MVKeyRange<IntegerKey>(new IntegerKey(1),
            new IntegerKey(5), 2, 7).toString());
    }

}
