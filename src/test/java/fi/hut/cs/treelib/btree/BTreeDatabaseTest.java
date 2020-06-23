package fi.hut.cs.treelib.btree;

import fi.hut.cs.treelib.AbstractDatabaseTest;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.gui.TreeTester;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;

public class BTreeDatabaseTest extends
    AbstractDatabaseTest<IntegerKey, BTreePage<IntegerKey, StringValue>> {

    public static final int PAGE_SIZE = 200;

    public BTreeDatabaseTest() {
        super(IntegerKey.PROTOTYPE);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected BTreeDatabase<IntegerKey, StringValue> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    @Override
    protected BTreeDatabase<IntegerKey, StringValue> createDatabase(PageStorage storage) {
        return new BTreeDatabase<IntegerKey, StringValue>(32, nonThrashingPolicy,
            IntegerKey.PROTOTYPE, StringValue.PROTOTYPE, storage);
    }

    @Override
    protected int getExpectedPageCountInSampleTree() {
        return 5;
    }

    public void testBTreeStorage() {
        PageStorage storage = new MemoryPageStorage(PAGE_SIZE);
        BTreeDatabase<IntegerKey, StringValue> db = createDatabase(storage);

        assertNull(db.getDatabaseTree().getRoot(TEST_OWNER));
        Transaction<IntegerKey, StringValue> tx = db.beginTransaction();
        insert(tx, "10");
        assertEquals(1, db.getPageBuffer().getTotalPageFixes());
        insert(tx, "15");
        insert(tx, "3");

        PageBuffer buffer = db.getPageBuffer();
        Page<IntegerKey, StringValue> root = db.getDatabaseTree().getRoot(TEST_OWNER);
        assertNotNull(root);
        assertEquals(2, root.getPageID().intValue());
        buffer.unfix(root, TEST_OWNER);

        assertEquals(1, buffer.getTotalPageFixes());
        tx.commit();
        // Flush old tree to page storage
        db.flush();
        assertEquals(1, buffer.getTotalPageFixes());

        db = createDatabase(storage);

        buffer = db.getPageBuffer();
        root = db.getDatabaseTree().getRoot(TEST_OWNER);
        assertNotNull(root);
        assertEquals(2, root.getPageID().intValue());
        buffer.unfix(root, TEST_OWNER);

        tx = db.beginTransaction();
        assertEquals("10", tx.get(new IntegerKey(10)).getValue());
        assertEquals("15", tx.get(new IntegerKey(15)).getValue());
        assertEquals("3", tx.get(new IntegerKey(3)).getValue());
        tx.commit();
    }

    public void testFloorEntry() {
        BTreeDatabase<IntegerKey, StringValue> db = createDatabase();
        execute(db, TreeTester.FILLED_TREE_2);
        BTree<IntegerKey, StringValue> tree = db.getDatabaseTree();
        assertNull(tree.floorEntry(new IntegerKey(2), dummyTX));
        assertNull(tree.floorEntry(new IntegerKey(0), dummyTX));
        assertEquals("3", tree.floorEntry(new IntegerKey(3), dummyTX).getSecond().getValue());
        assertEquals("7", tree.floorEntry(new IntegerKey(8), dummyTX).getSecond().getValue());
        assertEquals("7", tree.floorEntry(new IntegerKey(9), dummyTX).getSecond().getValue());
        assertEquals("10", tree.floorEntry(new IntegerKey(10), dummyTX).getSecond().getValue());
        assertEquals("123", tree.floorEntry(new IntegerKey(345747), dummyTX).getSecond()
            .getValue());
    }
}
