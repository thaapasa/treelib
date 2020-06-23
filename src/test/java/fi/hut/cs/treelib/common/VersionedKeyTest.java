package fi.hut.cs.treelib.common;

import junit.framework.TestCase;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.internal.KeyRangeImpl;

public class VersionedKeyTest extends TestCase {

    public void testContains() {
        VersionedKey<IntegerKey> min = new VersionedKey<IntegerKey>(IntegerKey.MIN_KEY,
            Integer.MIN_VALUE);
        VersionedKey<IntegerKey> max = new VersionedKey<IntegerKey>(IntegerKey.MAX_KEY,
            Integer.MAX_VALUE);

        VersionedKey<IntegerKey> t = new VersionedKey<IntegerKey>(new IntegerKey(5), 10);

        KeyRange<VersionedKey<IntegerKey>> r1 = new KeyRangeImpl<VersionedKey<IntegerKey>>(min,
            max);
        KeyRange<VersionedKey<IntegerKey>> r2 = t.getEntireRange();

        assertEquals(r1, r2);
        assertTrue(r1.contains(t));
        assertTrue(r1.contains(min));
        assertFalse(r1.contains(max));
        assertTrue(r1.contains(max.previousKey()));

        VersionedKey<IntegerKey> t2 = new VersionedKey<IntegerKey>(IntegerKey.MIN_KEY,
            Integer.MAX_VALUE);
        assertTrue(r1.contains(t2));
    }

    public void testParse() {
        VersionedKey<IntegerKey> proto = new VersionedKey<IntegerKey>(IntegerKey.PROTOTYPE, 0);
        VersionedKey<IntegerKey> k = proto.parse("5@10");
        assertEquals(5, k.getKey().intValue());
        assertEquals(10, k.getVersion());

        k = proto.parse("  5 @ 10  ");
        assertEquals(5, k.getKey().intValue());
        assertEquals(10, k.getVersion());

        k = proto.parse("-100@7");
        assertEquals(-100, k.getKey().intValue());
        assertEquals(7, k.getVersion());
    }

}
