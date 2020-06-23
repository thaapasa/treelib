package fi.hut.cs.treelib;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import junit.framework.TestCase;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.internal.KeyRangeImpl;

public class KeyRangeTest extends TestCase {

    private KeyRange<IntegerKey> createRange(int min, int max) {
        return new KeyRangeImpl<IntegerKey>(new IntegerKey(min), new IntegerKey(max));
    }

    private SortedSet<KeyRange<IntegerKey>> getSet() {
        SortedSet<KeyRange<IntegerKey>> set = new TreeSet<KeyRange<IntegerKey>>();
        set.add(createRange(5, 10));
        set.add(createRange(10, 15));
        set.add(createRange(1, 5));
        set.add(createRange(15, 20));
        return set;
    }

    public void testRangeSort() {
        SortedSet<KeyRange<IntegerKey>> set = getSet();
        Iterator<KeyRange<IntegerKey>> it = set.iterator();
        assertTrue(it.hasNext());
        assertEquals(createRange(1, 5), it.next());
        assertTrue(it.hasNext());
        assertEquals(createRange(5, 10), it.next());
        assertTrue(it.hasNext());
        assertEquals(createRange(10, 15), it.next());
        assertTrue(it.hasNext());
        assertEquals(createRange(15, 20), it.next());
        assertFalse(it.hasNext());
    }

    public void testSetContains() {
        SortedSet<KeyRange<IntegerKey>> set = getSet();
        assertTrue(set.contains(createRange(1, 5)));
        assertTrue(set.contains(createRange(5, 10)));
        assertTrue(set.contains(createRange(10, 15)));
        assertTrue(set.contains(createRange(15, 20)));
        assertFalse(set.contains(createRange(1, 4)));
        assertFalse(set.contains(createRange(6, 10)));
    }

    public void testRangeContains() {
        KeyRange<IntegerKey> range = createRange(1, 5);
        assertFalse(range.contains("0"));
        assertTrue(range.contains("1"));
        assertTrue(range.contains("2"));
        assertTrue(range.contains("3"));
        assertTrue(range.contains("4"));
        assertFalse(range.contains("5"));
        assertFalse(range.contains("6"));
    }

    public void testRangeOverlaps() {
        KeyRange<IntegerKey> range = createRange(1, 5);
        assertTrue(range.overlaps(createRange(1, 5)));
        assertTrue(range.overlaps(createRange(2, 3)));
        assertTrue(range.overlaps(createRange(-100, 10)));

        assertTrue(range.overlaps(createRange(3, 10)));
        assertTrue(range.overlaps(createRange(4, 10)));
        assertTrue(range.overlaps(createRange(-3, 2)));

        assertFalse(range.overlaps(createRange(5, 10)));
        assertFalse(range.overlaps(createRange(6, 10)));

        assertFalse(range.overlaps(createRange(-3, 1)));
        assertFalse(range.overlaps(createRange(-3, 0)));
    }

}
