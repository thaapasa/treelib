package fi.hut.cs.treelib.mvbt;

import fi.hut.cs.treelib.AbstractMVDatabaseTest;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;

public class MVBTDatabaseTest extends
    AbstractMVDatabaseTest<IntegerKey, MVBTPage<IntegerKey, StringValue>> {

    public static final int PAGE_SIZE = 450;

    public MVBTDatabaseTest() {
        super(IntegerKey.PROTOTYPE);
    }

    @Override
    protected boolean isTransactionSupported() {
        return false;
    }

    @Override
    protected MVBTDatabase<IntegerKey, StringValue> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    @Override
    protected MVBTDatabase<IntegerKey, StringValue> createDatabase(PageStorage storage) {
        return new MVBTDatabase<IntegerKey, StringValue>(32, nonThrashingPolicy,
            IntegerKey.PROTOTYPE, StringValue.PROTOTYPE, storage);
    }

    @Override
    protected boolean isDefaultClearUsed() {
        return false;
    }

    protected MVBTDatabase<IntegerKey, StringValue> getFilledDB2() {
        MVBTDatabase<IntegerKey, StringValue> db = createDatabase();
        Transaction<IntegerKey, StringValue> tx = db.beginTransaction();

        insert(tx, "10");
        insert(tx, "5");
        insert(tx, "7");
        insert(tx, "15");
        insert(tx, "123");
        insert(tx, "3");
        insert(tx, "4");
        insert(tx, "18");
        insert(tx, "24");
        insert(tx, "25");
        tx.commit();
        return db;
    }

    public void testKeyRangesAfterMerge() {
        MVBTDatabase<IntegerKey, StringValue> db = getFilledDB2();
        Transaction<IntegerKey, StringValue> tx = db.beginTransaction();
        insert(tx, "30");
        insert(tx, "67");
        insert(tx, "100");
        delete(tx, "100");
        delete(tx, "25");
        delete(tx, "30");

        delete(tx, "10");
        tx.commit();
    }

    public void testChildPageVersionAfterSplit() {
        MVBTDatabase<IntegerKey, StringValue> db = getFilledDB2();
        Transaction<IntegerKey, StringValue> tx = db.beginTransaction();
        PageBuffer buffer = db.getPageBuffer();

        for (int i = 150; i < 160; i++) {
            insert(tx, String.valueOf(i));
        }

        MVBTPage<IntegerKey, StringValue> root = db.getDatabaseTree().getRoot(TEST_OWNER);
        assertNotNull(root);
        assertEquals(2, root.getChildren().size());
        MVBTPage<IntegerKey, StringValue> page2 = (MVBTPage<IntegerKey, StringValue>) root
            .getChildren().get(1);
        buffer.unfix(root, TEST_OWNER);
        assertNotNull(page2);
    }

    public static final String[] DEL_PAGE_COPY_OPER = new String[] { "b", "i;10", "i;20", "i;15",
        "i;25", "i;4", "i;5", "i;6", "i;20", "i;21", "i;22", "i;8", "i;12", "i;17", "i;1", "i;2",
        "i;3", "i;7", "d;5", "d;4", "i;4", "d;6", "d;7", "d;21", "d;22" };

    public void testDeletedPageCopying() {
        MVBTDatabase<IntegerKey, StringValue> db = createDatabase();
        execute(db, DEL_PAGE_COPY_OPER);
    }

    @Override
    protected int getBufferFixesAfterActions() {
        // Root page of MVBT + root page of root*
        return 2;
    }

    @Override
    protected int getBufferFixesAfterAllDeleted() {
        // Root page of root*
        return 1;
    }

}
