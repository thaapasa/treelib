package fi.hut.cs.treelib.tsb;

import fi.hut.cs.treelib.AbstractMVDatabaseTest;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.tsb.TSBOperations.SplitPolicy;
import fi.tuska.util.Triple;

public abstract class AbstractTSBDatabaseTest extends
    AbstractMVDatabaseTest<IntegerKey, TSBPage<IntegerKey, StringValue>> {

    private static final int PAGE_SIZE = 350;

    protected final SplitPolicy splitPolicy;
    protected final boolean batchPTTUpdate;

    public AbstractTSBDatabaseTest(SplitPolicy splitPolicy, boolean batchPTTUpdate) {
        super(IntegerKey.PROTOTYPE);
        this.splitPolicy = splitPolicy;
        this.batchPTTUpdate = batchPTTUpdate;
    }

    @Override
    protected TSBDatabase<IntegerKey, StringValue> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    @Override
    protected TSBDatabase<IntegerKey, StringValue> createDatabase(PageStorage storage) {
        TSBDatabase<IntegerKey, StringValue> db = new TSBDatabase<IntegerKey, StringValue>(32,
            IntegerKey.PROTOTYPE, StringValue.PROTOTYPE, splitPolicy, batchPTTUpdate, storage);
        return db;
    }

    @Override
    protected boolean isDefaultClearUsed() {
        return false;
    }

    @Override
    protected int getBufferFixesAfterAllDeleted() {
        // Root page and PTT root page
        return 2;
    }

    @Override
    protected int getBufferFixesAfterActions() {
        // Root page, PTT not yet initialized
        return 1;
    }

    @Override
    protected boolean isOverlappingTransactionSupported() {
        return true;
    }

    private Transaction<IntegerKey, IntegerValue> pttTX = new DummyTransaction<IntegerKey, IntegerValue>(
        TEST_OWNER);

    private <K extends Key<K>, V extends PageValue<?>> void checkRefCount(TSBTree<K, V> tree,
        int txID, Integer expected, Integer expectedC) {
        Triple<Integer, Integer, Boolean> vttV = tree.getVTT().get(txID);
        IntegerValue pttV = tree.getPTT().get(new IntegerKey(txID), pttTX);
        if (expected == null) {
            assertNull(vttV);
            assertNull(pttV);
            return;
        }
        assertNotNull(vttV);
        assertEquals(expected, vttV.getSecond());

        if (expectedC != null) {
            assertNotNull(pttV);
            assertEquals(expectedC, vttV.getFirst());
            assertEquals(expectedC, pttV.getValue());
        } else {
            assertNull(vttV.getFirst());
            assertNull(pttV);
        }
    }

    public void testPTTClearing() {
        TSBDatabase<IntegerKey, StringValue> db = createDatabase();
        Transaction<IntegerKey, StringValue> tx = db.beginTransaction();
        for (int i = 1; i <= 20; i++) {
            insert(tx, String.valueOf(i));
        }
        int txID = tx.getTransactionID();
        checkRefCount(db.getDatabaseTree(), txID, 20, null);
        tx.commit();
        db.requestMaintenance();
        assertEquals(1, tx.getCommitVersion());
        checkRefCount(db.getDatabaseTree(), txID, 20, 1);

        tx = db.beginTransaction();
        delete(tx, "1");
        checkRefCount(db.getDatabaseTree(), txID, 19, 1);
        delete(tx, "3");
        checkRefCount(db.getDatabaseTree(), txID, 18, 1);
        tx.commit();
        checkRefCount(db.getDatabaseTree(), txID, 18, 1);

        tx = db.beginReadTransaction(2);
        tx.getRange(IntegerKey.ENTIRE_RANGE);
        checkRefCount(db.getDatabaseTree(), txID, null, null);
        tx.commit();
        checkRefCount(db.getDatabaseTree(), txID, null, null);
    }

    public void testPTTUpdate() {
        TSBDatabase<IntegerKey, StringValue> db = createDatabase();

        TSBTree<IntegerKey, StringValue> tree = db.getDatabaseTree();
        BTree<IntegerKey, IntegerValue> ptt = tree.getPTT();

        DummyTransaction<IntegerKey, IntegerValue> pttTX = new DummyTransaction<IntegerKey, IntegerValue>(
            "ptt-test");
        assertTrue(ptt.isEmpty(pttTX));

        Transaction<IntegerKey, StringValue> tx = db.beginTransaction();
        tx.insert(k(1), v(12));
        tx.insert(k(3), v(12));

        Transaction<IntegerKey, StringValue> tx2 = db.beginTransaction();
        tx2.insert(k(2), v(26));
        tx2.insert(k(6), v(23));

        tx2.commit();

        assertEquals(1, ptt.get(k(tx2.getTransactionID()), pttTX).intValue());
        assertNull(ptt.get(k(tx.getTransactionID()), pttTX));

        tx.commit();

        assertEquals(1, ptt.get(k(tx2.getTransactionID()), pttTX).intValue());
        assertEquals(2, ptt.get(k(tx.getTransactionID()), pttTX).intValue());
    }

    public void testTXOwnLeafEntryManipulation() {
        TSBDatabase<IntegerKey, StringValue> db = createDatabase();
        Transaction<IntegerKey, StringValue> tx = db.beginTransaction();
        insert(tx, "1");
        insert(tx, "3");
        insert(tx, "6");
        checkLeafPageContents(k(3), 3, db);

        // Check that overwriting does not create new entries
        insert(tx, "3");
        checkLeafPageContents(k(3), 3, db);

        // Check that the deletion is physical
        delete(tx, "6");
        checkLeafPageContents(k(3), 2, db);

        tx.commit();

        tx = db.beginTransaction();

        insert(tx, "7");
        checkLeafPageContents(k(3), 3, db);

        // Overwrite
        insert(tx, "3");
        checkLeafPageContents(k(3), 4, db);

        // Delete own overwriting entry, replace with delete marker
        delete(tx, "3");
        checkLeafPageContents(k(3), 4, db);

        // Delete own new entry, physical deletion
        delete(tx, "7");
        checkLeafPageContents(k(3), 3, db);

        tx.commit();
    }

    private void checkLeafPageContents(IntegerKey key, int expectedCount,
        TSBDatabase<IntegerKey, StringValue> db) {
        TSBPage<IntegerKey, StringValue> p = db.getDatabaseTree().getPage(key, TEST_OWNER);
        assertEquals(expectedCount, p.getEntryCount());
        p.getEntryMap().checkConsistency();
        PageBuffer buf = db.getPageBuffer();
        buf.unfix(p, TEST_OWNER);
    }

    public void testPageSizes() {
        PageStorage storage = new MemoryPageStorage(PAGE_SIZE);
        TSBDatabase<IntegerKey, IntegerValue> db = new TSBDatabase<IntegerKey, IntegerValue>(32,
            IntegerKey.PROTOTYPE, IntegerValue.PROTOTYPE, splitPolicy, batchPTTUpdate, storage);

        TSBTree<IntegerKey, IntegerValue> tree = db.getDatabaseTree();
        TSBPage<IntegerKey, IntegerValue> p = new TSBPage<IntegerKey, IntegerValue>(tree,
            new PageID(1), PAGE_SIZE);

        // Leaf page
        p.format(1);
        // Key size = 4 + 4 + 4 = 12
        assertEquals(12, p.getSingleEntrySize());
        assertEquals(26, p.getPageEntryCapacity());

        // Index page
        p.format(2);
        // Key size = 4 * 4 + 4 = 20
        assertEquals(20, p.getSingleEntrySize());
        assertEquals(15, p.getPageEntryCapacity());
    }

}
