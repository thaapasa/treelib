package fi.hut.cs.treelib.tsb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.TransactionIDManager;
import fi.hut.cs.treelib.TreeLibDBTest;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.ListCreatingCallback;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.hut.cs.treelib.tsb.TSBEntryMap;
import fi.tuska.util.Pair;
import fi.tuska.util.Quad;
import fi.tuska.util.Triple;

public class TSBEntryMapTest extends TreeLibDBTest<IntegerKey, IntegerValue> implements
    TransactionIDManager<IntegerKey> {

    private Map<Integer, Integer> vtt = new HashMap<Integer, Integer>();

    public TSBEntryMapTest() {
        super(IntegerKey.PROTOTYPE, IntegerValue.PROTOTYPE);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        vtt.clear();
    }

    private Quad<IntegerKey, Integer, Boolean, UpdateMarker<IntegerValue>> t4(int k, int ver,
        boolean isCommitted, int val, boolean del) {
        return new Quad<IntegerKey, Integer, Boolean, UpdateMarker<IntegerValue>>(k(k), ver,
            isCommitted, del ? UpdateMarker.createDelete(IntegerValue.PROTOTYPE) : UpdateMarker
                .createInsert(v(val)));
    }

    private Triple<MVKeyRange<IntegerKey>, Boolean, IntegerValue> t3(int k, int v1, int v2,
        boolean isCommitted, int val) {
        return new Triple<MVKeyRange<IntegerKey>, Boolean, IntegerValue>(
            new MVKeyRange<IntegerKey>(k(k), v1, v2), isCommitted, v(val));
    }

    @Override
    public Integer getCommitVersion(int transactionID) {
        return vtt.get(transactionID);
    }

    @Override
    public void notifyTempConverted(IntegerKey key, int transactionID, int commitTime) {
    }

    @Override
    public void notifyTempInserted(IntegerKey key, int transactionID) {
    }

    public TSBEntryMap<IntegerKey, IntegerValue> getFilledMap() {
        TSBEntryMap<IntegerKey, IntegerValue> map = new TSBEntryMap<IntegerKey, IntegerValue>(
            this, IntegerKey.PROTOTYPE, IntegerValue.PROTOTYPE);

        DummyTransaction<IntegerKey, IntegerValue> tx = new DummyTransaction<IntegerKey, IntegerValue>(
            0, 0, TEST_OWNER);

        // tx ID 1 will be committed with version 4
        tx.setTransactionID(1);
        map.insert(k(1), v(11), tx);
        map.insert(k(2), v(12), tx);
        map.insert(k(3), v(13), tx);

        // tx ID 2 will be left uncommitted
        tx.setTransactionID(2);
        map.delete(k(1), tx);
        map.insert(k(3), v(23), tx);
        map.insert(k(5), v(25), tx);
        map.insert(k(6), v(26), tx);

        // tx ID 3 will be committed with version 5
        tx.setTransactionID(3);
        map.delete(k(1), tx);
        map.insert(k(5), v(35), tx);

        return map;
    }

    public void testTempDeletion() {
        TSBEntryMap<IntegerKey, IntegerValue> map = new TSBEntryMap<IntegerKey, IntegerValue>(
            this, IntegerKey.PROTOTYPE, IntegerValue.PROTOTYPE);

        DummyTransaction<IntegerKey, IntegerValue> tx = new DummyTransaction<IntegerKey, IntegerValue>(
            0, 0, TEST_OWNER);
        tx.setTransactionID(1);
        map.insert(k(1), v(1), tx);
        map.insert(k(2), v(2), tx);
        map.insert(k(3), v(3), tx);
        vtt.put(1, 1);
        tx.setTransactionID(3);
        map.delete(k(2), tx);

        tx.set(1, 3, false);
        assertEquals(kvl(1, 1, 3, 3), getRange(map, kr(1, 4), tx));
        map.printDebugInfo();
    }

    protected <K extends Key<K>, V extends PageValue<?>> List<Pair<K, V>> getRange(
        TSBEntryMap<K, V> map, KeyRange<K> range, Transaction<K, V> tx) {
        ListCreatingCallback<Pair<K, V>> lc = new ListCreatingCallback<Pair<K, V>>();
        map.getRange(range, tx, lc);
        return lc.getList();
    }

    public void testMapSplit() {
        TSBEntryMap<IntegerKey, IntegerValue> map = getFilledMap();
        // Commit tx 1 (commit-id 4)
        vtt.put(1, 4);
        // Commit tx 3 (commit-id 5)
        vtt.put(3, 5);
        map.updateAllTemporaries();

        map.printDebugInfo();
        assertEquals(9, map.size());

        System.out.println("Split");

        TSBEntryMap<IntegerKey, IntegerValue> map2 = map.split(5);
        map.printDebugInfo();
        map2.printDebugInfo();

        Iterator<Triple<MVKeyRange<IntegerKey>, Boolean, IntegerValue>> it = map
            .logicalIterator();

        assertTrue(it.hasNext());
        assertEquals(t3(1, 4, Integer.MAX_VALUE, true, 11), it.next());
        assertEquals(t3(2, 4, Integer.MAX_VALUE, true, 12), it.next());
        assertEquals(t3(3, 4, Integer.MAX_VALUE, true, 13), it.next());
        assertFalse(it.hasNext());
        assertEquals(3, map.size());

        it = map2.logicalIterator();

        assertTrue(it.hasNext());
        assertEquals(t3(2, 5, Integer.MAX_VALUE, true, 12), it.next());
        assertEquals(t3(3, 5, Integer.MAX_VALUE, true, 13), it.next());
        assertEquals(t3(3, 2, Integer.MAX_VALUE, false, 23), it.next());
        assertEquals(t3(5, 5, Integer.MAX_VALUE, true, 35), it.next());
        assertEquals(t3(5, 2, Integer.MAX_VALUE, false, 25), it.next());
        assertTrue(it.hasNext());
        assertEquals(t3(6, 2, Integer.MAX_VALUE, false, 26), it.next());
        assertFalse(it.hasNext());
        assertEquals(8, map2.size());
    }

    public void testLogicalIterator() {
        TSBEntryMap<IntegerKey, IntegerValue> map = getFilledMap();

        // Commit tx 1 (commit-id 4)
        vtt.put(1, 4);
        // Commit tx 3 (commit-id 5)
        vtt.put(3, 5);
        map.updateAllTemporaries();

        Iterator<Triple<MVKeyRange<IntegerKey>, Boolean, IntegerValue>> it = map
            .logicalIterator();
        assertTrue(it.hasNext());
        assertEquals(t3(1, 4, 5, true, 11), it.next());
        assertEquals(t3(2, 4, Integer.MAX_VALUE, true, 12), it.next());
        assertEquals(t3(3, 4, Integer.MAX_VALUE, true, 13), it.next());
        assertTrue(it.hasNext());
        assertEquals(t3(3, 2, Integer.MAX_VALUE, false, 23), it.next());
        assertEquals(t3(5, 5, Integer.MAX_VALUE, true, 35), it.next());
        assertEquals(t3(5, 2, Integer.MAX_VALUE, false, 25), it.next());
        assertTrue(it.hasNext());
        assertEquals(t3(6, 2, Integer.MAX_VALUE, false, 26), it.next());
        assertFalse(it.hasNext());
    }

    public void testTemporaryGets() {
        TSBEntryMap<IntegerKey, IntegerValue> map = getFilledMap();

        DummyTransaction<IntegerKey, IntegerValue> tx = new DummyTransaction<IntegerKey, IntegerValue>(
            0, 0, TEST_OWNER);

        // Gets for read-only tx
        for (int k = 0; k < 8; k++) {
            for (int read = 0; read < 3; read++) {
                tx.set(read, 0, true);
                assertNull(map.get(k(k), tx));
                tx.setTransactionID(1);
                assertNull(map.get(k(k), tx));
                tx.setTransactionID(2);
                assertNull(map.get(k(k), tx));
            }
        }

        // Gets for the updating tx 1
        tx.set(1, 1, false);
        assertNull(map.get(k(0), tx));
        assertEquals(v(11), map.get(k(1), tx));
        assertEquals(v(12), map.get(k(2), tx));
        assertEquals(v(13), map.get(k(3), tx));
        assertNull(map.get(k(4), tx));
        assertNull(map.get(k(5), tx));
        assertNull(map.get(k(6), tx));
        assertNull(map.get(k(7), tx));

        // Gets for updating tx 2
        tx.set(1, 2, false);
        assertNull(map.get(k(1), tx));
        assertEquals(v(23), map.get(k(3), tx));
        assertNull(map.get(k(4), tx));
        assertEquals(v(25), map.get(k(5), tx));
        assertEquals(v(26), map.get(k(6), tx));
        assertNull(map.get(k(7), tx));

        tx.set(1, 1, true);
        assertEquals(kvl(), getRange(map, kr(0, 10), tx));
        tx.set(1, 1, false);
        assertEquals(kvl(1, 11, 2, 12, 3, 13), getRange(map, kr(0, 10), tx));
        tx.set(4, 15, true);
        assertEquals(kvl(), getRange(map, kr(0, 10), tx));
    }

    public void testCommittedGets() {
        TSBEntryMap<IntegerKey, IntegerValue> map = getFilledMap();

        DummyTransaction<IntegerKey, IntegerValue> tx = new DummyTransaction<IntegerKey, IntegerValue>(
            0, 0, TEST_OWNER);

        // Commit tx 1 (commit-id 4)
        vtt.put(1, 4);

        tx.set(4, 15, true);
        assertEquals(kvl(1, 11, 2, 12, 3, 13), getRange(map, kr(0, 10), tx));
        assertEquals(kvl(2, 12, 3, 13), getRange(map, kr(2, 4), tx));
        assertEquals(kvl(1, 11, 2, 12), getRange(map, kr(1, 3), tx));

        // 1 committed, values should reflect tx1 after ver. 4
        tx.set(1, 10, true);
        assertNull(map.get(k(1), tx));
        tx.setReadVersion(3);
        assertNull(map.get(k(1), tx));
        tx.setReadVersion(4);
        assertEquals(v(11), map.get(k(1), tx));
        tx.setReadVersion(5);
        assertEquals(v(11), map.get(k(1), tx));
        tx.setReadVersion(10);
        assertEquals(v(11), map.get(k(1), tx));

        tx.set(3, 10, true);
        assertNull(map.get(k(3), tx));
        tx.setReadVersion(4);
        assertEquals(v(13), map.get(k(3), tx));
        tx.setReadVersion(5);
        assertEquals(v(13), map.get(k(3), tx));
        tx.setReadVersion(10);
        assertEquals(v(13), map.get(k(3), tx));

        // Commit tx 3 (commit-id 5)
        vtt.put(3, 5);

        tx.set(4, 15, true);
        assertEquals(kvl(1, 11, 2, 12, 3, 13), getRange(map, kr(0, 10), tx));
        tx.set(5, 15, true);
        assertEquals(kvl(2, 12, 3, 13, 5, 35), getRange(map, kr(0, 10), tx));
        tx.set(5, 3, true);
        assertEquals(kvl(2, 12, 3, 13, 5, 35), getRange(map, kr(0, 10), tx));
        tx.set(4, 2, false);
        assertEquals(kvl(2, 12, 3, 23, 5, 25, 6, 26), getRange(map, kr(0, 10), tx));

        // Key 1
        tx.set(2, 10, true);
        assertNull(map.get(k(1), tx));
        tx.setReadVersion(3);
        assertNull(map.get(k(1), tx));
        tx.setReadVersion(4);
        assertEquals(v(11), map.get(k(1), tx));
        tx.setReadVersion(5);
        assertNull(map.get(k(1), tx));
        tx.setReadVersion(6);
        assertNull(map.get(k(1), tx));

        // Key 2
        tx.set(2, 10, true);
        assertNull(map.get(k(2), tx));
        tx.setReadVersion(3);
        assertNull(map.get(k(2), tx));
        tx.setReadVersion(4);
        assertEquals(v(12), map.get(k(2), tx));
        tx.setReadVersion(5);
        assertEquals(v(12), map.get(k(2), tx));
        tx.setReadVersion(9);
        assertEquals(v(12), map.get(k(2), tx));

        // Key 5
        tx.set(2, 10, false);
        assertNull(map.get(k(5), tx));
        tx.setReadVersion(3);
        assertNull(map.get(k(5), tx));
        tx.setReadVersion(4);
        assertNull(map.get(k(5), tx));
        tx.setReadVersion(5);
        assertEquals(v(35), map.get(k(5), tx));
        tx.setReadVersion(9);
        assertEquals(v(35), map.get(k(5), tx));

        System.out.println(map);
    }

    public void testIterator() {
        TSBEntryMap<IntegerKey, IntegerValue> map = getFilledMap();

        // Commit tx 1 (commit-id 4)
        vtt.put(1, 4);
        // Commit tx 3 (commit-id 5)
        vtt.put(3, 5);
        map.updateAllTemporaries();

        Iterator<Quad<IntegerKey, Integer, Boolean, UpdateMarker<IntegerValue>>> it = map
            .iterator();
        assertEquals(9, map.size());
        assertEquals(t4(1, 4, true, 11, false), it.next());
        assertEquals(t4(1, 5, true, 0, true), it.next());
        assertEquals(t4(1, 2, false, 0, true), it.next());
        assertEquals(t4(2, 4, true, 12, false), it.next());
        assertTrue(it.hasNext());
        assertEquals(9, map.size());
        assertEquals(t4(3, 4, true, 13, false), it.next());
        assertEquals(t4(3, 2, false, 23, false), it.next());
        assertEquals(t4(5, 5, true, 35, false), it.next());
        assertEquals(t4(5, 2, false, 25, false), it.next());
        assertTrue(it.hasNext());
        assertEquals(t4(6, 2, false, 26, false), it.next());
        assertFalse(it.hasNext());

        it = map.iterator();
        int i = 9;
        while (it.hasNext()) {
            assertEquals(i--, map.size());
            it.next();
            it.remove();
        }
        assertEquals(0, map.size());
        assertEquals("Entries: {}", map.toString());
    }

}
