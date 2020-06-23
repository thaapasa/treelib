package fi.hut.cs.treelib.mvbt;

import java.util.Iterator;
import java.util.List;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.TreeLibDBTest;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.ListCreatingCallback;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.tuska.util.Pair;
import fi.tuska.util.Triple;

public class MVBTEntryMapTest extends TreeLibDBTest<IntegerKey, IntegerValue> {

    private static final int PAGE_SIZE = 4096;

    public MVBTEntryMapTest() {
        super(IntegerKey.PROTOTYPE, IntegerValue.PROTOTYPE);
    }

    private Triple<IntegerKey, Integer, UpdateMarker<IntegerValue>> e3(int k, int v, Integer val) {
        return new Triple<IntegerKey, Integer, UpdateMarker<IntegerValue>>(k(k), v,
            val != null ? UpdateMarker.createInsert(v(val)) : UpdateMarker
                .createDelete(valueProto));
    }

    private Triple<MVKeyRange<IntegerKey>, Boolean, IntegerValue> t3(int k1, int v1, int v2,
        int val) {
        return new Triple<MVKeyRange<IntegerKey>, Boolean, IntegerValue>(
            new MVKeyRange<IntegerKey>(k(k1), v1, v2), true, v(val));
    }

    private MVBTEntryMap<IntegerKey, IntegerValue> getMap() {
        MVBTDatabase<IntegerKey, IntegerValue> db = new MVBTDatabase<IntegerKey, IntegerValue>(
            32, nonThrashingPolicy, IntegerKey.PROTOTYPE, IntegerValue.PROTOTYPE,
            new MemoryPageStorage(PAGE_SIZE));

        DummyTransaction<IntegerKey, IntegerValue> tx = new DummyTransaction<IntegerKey, IntegerValue>(
            0, 0, TEST_OWNER);
        MVBTPage<IntegerKey, IntegerValue> p = db.getDatabaseTree().createLeafRoot(tx);
        MVBTEntryMap<IntegerKey, IntegerValue> map = new MVBTEntryMap<IntegerKey, IntegerValue>(p);
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());

        map.printDebugInfo();

        tx.set(1, 1, false);
        map.insert(k(1), v(11), tx);
        assertFalse(map.isEmpty());
        assertEquals(1, map.size());
        map.insert(k(3), v(13), tx);
        map.insert(k(7), v(17), tx);

        tx.set(2, 2, false);
        map.insert(k(1), v(21), tx);
        map.insert(k(2), v(22), tx);
        map.insert(k(5), v(25), tx);

        assertFalse(map.isEmpty());
        assertEquals(6, map.size());

        tx.set(3, 3, false);
        map.delete(k(1), tx);
        map.delete(k(3), tx);
        map.insert(k(4), v(34), tx);

        assertFalse(map.isEmpty());
        assertEquals(9, map.size());
        return map;
    }

    public void testIterators() {
        MVBTEntryMap<IntegerKey, IntegerValue> map = getMap();
        map.printDebugInfo();

        {
            Iterator<Triple<IntegerKey, Integer, UpdateMarker<IntegerValue>>> it = map.iterator();
            assertTrue(it.hasNext());
            assertEquals(e3(1, 1, 11), it.next());
            assertEquals(e3(1, 2, 21), it.next());
            assertEquals(e3(1, 3, null), it.next());

            assertEquals(e3(2, 2, 22), it.next());

            assertTrue(it.hasNext());
            assertEquals(e3(3, 1, 13), it.next());
            assertEquals(e3(3, 3, null), it.next());

            assertEquals(e3(4, 3, 34), it.next());
            assertEquals(e3(5, 2, 25), it.next());
            assertTrue(it.hasNext());
            assertEquals(e3(7, 1, 17), it.next());
            assertFalse(it.hasNext());
        }

        {
            Iterator<Triple<MVKeyRange<IntegerKey>, Boolean, IntegerValue>> it = map
                .logicalIterator();
            assertTrue(it.hasNext());
            assertEquals(t3(1, 1, 2, 11), it.next());
            assertEquals(t3(1, 2, 3, 21), it.next());
            assertEquals(t3(2, 2, Integer.MAX_VALUE, 22), it.next());
            assertTrue(it.hasNext());
            assertEquals(t3(3, 1, 3, 13), it.next());
            assertEquals(t3(4, 3, Integer.MAX_VALUE, 34), it.next());
            assertEquals(t3(5, 2, Integer.MAX_VALUE, 25), it.next());

            assertTrue(it.hasNext());
            assertEquals(t3(7, 1, Integer.MAX_VALUE, 17), it.next());
            assertFalse(it.hasNext());
        }

        int maxver = 81;
        map.page.setKeyRange(new MVKeyRange<IntegerKey>(k(-10), k(1000), 0, maxver));

        {
            Iterator<Triple<MVKeyRange<IntegerKey>, Boolean, IntegerValue>> it = map
                .logicalIterator();
            assertTrue(it.hasNext());
            assertEquals(t3(1, 1, 2, 11), it.next());
            assertEquals(t3(1, 2, 3, 21), it.next());
            assertEquals(t3(2, 2, maxver, 22), it.next());
            assertTrue(it.hasNext());
            assertEquals(t3(3, 1, 3, 13), it.next());
            assertEquals(t3(4, 3, maxver, 34), it.next());
            assertEquals(t3(5, 2, maxver, 25), it.next());

            assertTrue(it.hasNext());
            assertEquals(t3(7, 1, maxver, 17), it.next());
            assertFalse(it.hasNext());
        }

    }

    public void testGetRanges() {
        MVBTEntryMap<IntegerKey, IntegerValue> map = getMap();

        DummyTransaction<IntegerKey, IntegerValue> tx = new DummyTransaction<IntegerKey, IntegerValue>(
            0, 0, TEST_OWNER);
        tx.set(1, 1, false);
        assertEquals(kvl(1, 11, 3, 13, 7, 17), getRange(map, kr(0, 10), tx));
        tx.set(2, 2, false);
        assertEquals(kvl(1, 21, 2, 22, 3, 13, 5, 25, 7, 17), getRange(map, kr(0, 10), tx));
        tx.set(3, 3, false);
        assertEquals(kvl(2, 22, 4, 34, 5, 25, 7, 17), getRange(map, kr(0, 10), tx));

        assertEquals(kvl(2, 22, 4, 34, 5, 25, 7, 17), getRange(map, kr(2, 10), tx));
        assertEquals(kvl(4, 34, 5, 25, 7, 17), getRange(map, kr(3, 10), tx));

        assertEquals(kvl(2, 22, 4, 34, 5, 25, 7, 17), getRange(map, kr(2, 8), tx));
        assertEquals(kvl(2, 22, 4, 34, 5, 25), getRange(map, kr(2, 7), tx));
        assertEquals(kvl(2, 22, 4, 34), getRange(map, kr(2, 5), tx));
        assertEquals(kvl(2, 22, 4, 34), getRange(map, kr(0, 5), tx));
        assertEquals(kvl(4, 34), getRange(map, kr(3, 5), tx));
        assertEquals(kvl(4, 34), getRange(map, kr(4, 5), tx));
    }

    protected <K extends Key<K>, V extends PageValue<?>> List<Pair<K, V>> getRange(
        MVBTEntryMap<K, V> map, KeyRange<K> range, Transaction<K, V> tx) {
        ListCreatingCallback<Pair<K, V>> lc = new ListCreatingCallback<Pair<K, V>>();
        map.getRange(range, tx, lc);
        return lc.getList();
    }

}
