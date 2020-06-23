package fi.hut.cs.treelib.tsb;

import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.tsb.TSBOperations.SplitPolicy;

public class TSBDeferredDatabaseTest extends AbstractTSBDatabaseTest {

    public TSBDeferredDatabaseTest() {
        super(SplitPolicy.Deferred, true);
    }

    @Override
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

        assertNull(ptt.get(k(tx2.getTransactionID()), pttTX));
        assertNull(ptt.get(k(tx.getTransactionID()), pttTX));

        tx.commit();

        assertNull(ptt.get(k(tx2.getTransactionID()), pttTX));
        assertNull(ptt.get(k(tx.getTransactionID()), pttTX));

        db.requestMaintenance();

        assertEquals(1, ptt.get(k(tx2.getTransactionID()), pttTX).intValue());
        assertEquals(2, ptt.get(k(tx.getTransactionID()), pttTX).intValue());
    }

    public void testDeferredBitSaving() {
        TSBDatabase<IntegerKey, StringValue> db = createDatabase();
        Transaction<IntegerKey, StringValue> tx = db.beginTransaction();
        PageBuffer buffer = db.getPageBuffer();

        TSBPage<IntegerKey, StringValue> page = buffer.createPage(db.getDatabaseTree()
            .getPageFactory(), tx);
        PageID pageID = page.getPageID();
        page.format(1);
        page.insert(k(1), v(1), tx);

        assertNotNull(page);
        assertFalse(page.isDeferredSplit());
        buffer.unfix(pageID, tx);
        buffer.flush(true);

        page = buffer.fixPage(pageID, db.getDatabaseTree().getPageFactory(), false, tx);
        assertNotNull(page);
        assertFalse(page.isDeferredSplit());

        page.setDeferredSplit(true);
        buffer.unfix(pageID, tx);
        buffer.flush(true);

        page = buffer.fixPage(pageID, db.getDatabaseTree().getPageFactory(), false, tx);
        assertNotNull(page);
        assertTrue(page.isDeferredSplit());
    }

}
