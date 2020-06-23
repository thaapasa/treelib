package fi.hut.cs.treelib.mvbt;

import fi.hut.cs.treelib.AbstractMVDatabaseTest;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;

public class TMVBTDatabaseTest extends
    AbstractMVDatabaseTest<IntegerKey, MVBTPage<IntegerKey, StringValue>> {

    public static final String[] TMVBT_DECREASE_HEIGHT = new String[] { "b", "i;1", "i;2", "i;3",
        "i;4", "i;5", "i;6", "i;7", "i;8", "i;9", "i;10", "i;11", "i;12", "c", "b", "d;1", "d;2",
        "d;3", "d;4", "d;5", "d;6", "d;7" };

    public static final String[] TWO_MIN_PAGE_MERGE = new String[] { "b", "i;10", "i;5", "i;7",
        "i;15", "i;123", "i;3", "i;4", "i;18", "i;24", "i;25", "i;30", "i;67", "i;100", "c", "b",
        "d;100", "d;25", "d;30", "c", "b", "d;24", "d;18", "d;15", "d;3", "d;4", "d;5", "d;7" };

    public static final int PAGE_SIZE = 350;

    public TMVBTDatabaseTest() {
        super(IntegerKey.PROTOTYPE);
    }

    @Override
    protected TMVBTDatabase<IntegerKey, StringValue> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    @Override
    protected TMVBTDatabase<IntegerKey, StringValue> createDatabase(PageStorage storage) {
        return new TMVBTDatabase<IntegerKey, StringValue>(32, nonThrashingPolicy,
            IntegerKey.PROTOTYPE, StringValue.PROTOTYPE, storage);
    }

    @Override
    protected boolean isDefaultClearUsed() {
        return false;
    }

    public void testDatabaseRangeStoring() {
        PageStorage storage = new MemoryPageStorage(PAGE_SIZE);

        TMVBTDatabase<IntegerKey, StringValue> db = createDatabase(storage);
        Transaction<IntegerKey, StringValue> t = db.beginTransaction();
        for (int i = 4; i < 10; i++) {
            t.insert(new IntegerKey(i), new StringValue("V" + i));
        }
        t.commit();

        assertEquals(getKeyRange("0", "10", 1, 2), db.getDatabaseTree().getKeyRange());
        db.flush();

        db = createDatabase(storage);
        assertEquals(getKeyRange("0", "10", 1, 2), db.getDatabaseTree().getKeyRange());

    }

    public void testActivePageMerge() {
        TMVBTDatabase<IntegerKey, StringValue> db = createDatabase();
        execute(db, new String[] { "b", "i;1", "i;2", "i;10", "i;15", "i;3", "i;4", "i;7",
            "d;15", "d;10", "d;7" });
    }

    public void testTwoMinPageMerge() {
        TMVBTDatabase<IntegerKey, StringValue> db = createDatabase();
        // Bug found 28.8.2008: When we have (at a level) two inactive pages
        // that both have min entries, deleting an entry in the first causes
        // weak version overflow -> merge with sibling -> create live copy of
        // sibling
        execute(db, TWO_MIN_PAGE_MERGE);
    }

    public static final String[] ERROR_CASE_1 = new String[] { "b", "i;1", "i;2", "i;3", "i;4",
        "i;5", "i;6", "i;7", "commit", "begin", "i;8", "i;9", "i;10", "i;11", "i;12", "i;13",
        "i;14", "i;50", "i;55", "i;56", "i;56", "i;60", "i;61", "d;1", "d;2" };

    public void testErrorCase1() {
        TMVBTDatabase<IntegerKey, StringValue> db = createDatabase();
        execute(db, ERROR_CASE_1);
    }

    public void testDecreaseTreeHeight() {
        TMVBTDatabase<IntegerKey, StringValue> db = createDatabase();
        execute(db, TMVBT_DECREASE_HEIGHT);
        MVBTPage<IntegerKey, StringValue> root = db.getMVDatabase().getDatabaseTree().getRoot(1,
            TEST_OWNER);
        // Old root
        assertEquals(2, root.getHeight());
        for (MVKeyRange<IntegerKey> key : root.getContents().keySet()) {
            log.debug("MV-key range " + key);
            assertEquals(1, key.getMinVersion().intValue());
        }
        db.getPageBuffer().unfix(root, TEST_OWNER);

        // New root
        root = db.getMVDatabase().getDatabaseTree().getRoot(TEST_OWNER);
        assertEquals(1, root.getHeight());
        db.getPageBuffer().unfix(root, TEST_OWNER);
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

    @Override
    protected boolean isOverlappingTransactionSupported() {
        return false;
    }

}
