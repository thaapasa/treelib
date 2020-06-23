package fi.hut.cs.treelib;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import fi.hut.cs.treelib.common.AbstractTree;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.controller.TreeCreator;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.mvbt.MVBTPage;
import fi.hut.cs.treelib.storage.FilePageStorage;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Converter;
import fi.tuska.util.IntegerRange;
import fi.tuska.util.IntegerRangeProvider;
import fi.tuska.util.IteratorWrapper;
import fi.tuska.util.Pair;
import fi.tuska.util.file.FileUtils;

public abstract class AbstractDatabaseTest<K extends Key<K>, P extends Page<K, StringValue>>
    extends TreeLibDBTest<K, StringValue> {

    private static final Collection<Long> EMPTY_LIST = new ArrayList<Long>();

    protected int transactionID = AbstractTree.DEFAULT_TRANSACTION_ID;
    protected final DummyTransaction<K, StringValue> dummyTX;

    protected AbstractDatabaseTest(K keyPrototype) {
        super(keyPrototype, StringValue.PROTOTYPE);
        this.dummyTX = new DummyTransaction<K, StringValue>(AbstractTree.DEFAULT_READ_VERSION,
            AbstractTree.DEFAULT_TRANSACTION_ID, TEST_OWNER);
    }

    protected StringValue v(String val) {
        return new StringValue(val);
    }

    protected abstract Database<K, StringValue, P> createDatabase();

    protected abstract Database<K, StringValue, P> createDatabase(PageStorage storage);

    protected void execute(Database<K, StringValue, ?> db, String[] operations) {
        TreeCreator<K, StringValue> creator = new TreeCreator<K, StringValue>(db, CollectionUtils
            .getList(operations), keyProto);
        creator.runAll();
        db.checkConsistency();
    }

    protected void execute(Database<K, StringValue, P> db, String operations) {
        execute(db, operations.split("\n"));
    }

    protected static <A, B> void checkContents(Collection<A> a, Collection<B> b) {
        if (a == null || b == null) {
            assertNull(a);
            assertNull(b);
            return;
        }
        assertEquals(a + " != " + b, a.size(), b.size());
        for (B bVal : b) {
            assertTrue(a + " does not contain " + bVal, a.contains(bVal));
        }
    }

    protected void insert(Transaction<K, StringValue> tx, String value) {
        K key = parse(value);
        tx.insert(key, new StringValue(value));
        assertTrue("Tree does not contain key " + key, tx.contains(key));
        assertEquals(value, tx.get(key).getValue());
    }

    protected K parse(String value) {
        return keyProto.parse(value);
    }

    protected void delete(Transaction<K, StringValue> tx, String value) {
        K key = parse(value);
        boolean res = tx.delete(key);
        assertTrue("Delete successful for " + key, res);
        assertFalse(tx.contains(key));
        assertNull(tx.get(key));
    }

    protected Database<K, StringValue, P> getFilledDB() {
        Database<K, StringValue, P> db = createDatabase();
        Transaction<K, StringValue> tx = db.beginTransaction();
        insert(tx, "3");
        insert(tx, "1");
        insert(tx, "10");
        insert(tx, "20");
        insert(tx, "4");
        insert(tx, "15");
        insert(tx, "30");
        tx.commit();

        return db;
    }

    protected void printPageInfo(Database<K, StringValue, P> db, P page, String title,
        PrintStream s) {
        s.println(title);
        s.println(page);
        if (page == null)
            return;
        int vSize = page.isLeafPage() ? db.getValuePrototype().getByteDataSize()
            : PageID.PROTOTYPE.getByteDataSize();
        s.println(String.format(
            "Page size: %d, entry size %d (k: %d, kr: %d, v: %d), capacity %d/%d entries", page
                .getPageSize(), page.getSingleEntrySize(),
            db.getKeyPrototype().getByteDataSize(), page.getKeyRange().getByteDataSize(), vSize,
            page.getEntryCount(), page.getPageEntryCapacity()));
    }

    public void testCloseAndOpen() {
        showTestName();
        File dbDir = new File("db");
        assertTrue(dbDir.exists());
        File file1 = new File(dbDir, "test1.db");
        File file2 = new File(dbDir, "test2.db");

        if (file1.exists()) {
            assertTrue(file1.delete());
        }
        if (file2.exists()) {
            assertTrue(file2.delete());
        }
        assertFalse(file1.exists());
        assertFalse(file2.exists());

        Database<K, StringValue, P> db = createDatabase(new FilePageStorage(500, file1));
        Transaction<K, StringValue> tx = db.beginTransaction();
        assertNotNull(tx);
        tx.insert(k(3), v("three"));
        tx.insert(k(1), v("one"));
        tx.commit();

        db.close();

        assertTrue(file1.exists());
        assertFalse(file2.exists());

        assertTrue(FileUtils.copy(file1, file2));
        assertTrue(file1.exists());
        assertTrue(file2.exists());

        // Clear database
        assertTrue(file1.delete());

        assertFalse(file1.exists());

        db.reopen();
        // .. test db is empty
        tx = db.beginTransaction();
        assertTrue(tx.getAll().isEmpty());
        tx.commit();

        // Insert something
        tx = db.beginTransaction();
        tx.insert(k(2), v("two"));
        tx.insert(k(10), v("ten"));
        tx.commit();

        // Close db
        db.close();

        assertTrue(file1.exists());
        assertTrue(file2.exists());

        // Restore copied original DB
        assertTrue(FileUtils.copy(file2, file1));

        db.reopen();
        // .. test db contains original stuff
        tx = db.beginTransaction();
        List<Pair<K, StringValue>> contents = tx.getAll();
        assertEquals(2, contents.size());
        assertEquals(k(1), contents.get(0).getFirst());
        assertEquals(v("one"), contents.get(0).getSecond());
        assertEquals(k(3), contents.get(1).getFirst());
        assertEquals(v("three"), contents.get(1).getSecond());
        tx.commit();

        // Close DB
        db.close();

        // Delete DB files
        assertTrue(file1.delete());
        assertTrue(file2.delete());

        assertFalse(file1.exists());
        assertFalse(file2.exists());
    }

    public void testShowPageSizes() {
        showTestName();
        Database<K, StringValue, P> db = createDatabase();
        Transaction<K, StringValue> tx = db.beginTransaction();
        for (int i = 0; i < 100; i++) {
            insert(tx, String.valueOf(i));
        }
        tx.commit();
        try {
            PageBuffer buf = db.getPageBuffer();
            Tree<K, StringValue, P> tree = db.getDatabaseTree();

            P root = tree.getRoot(TEST_OWNER);
            printPageInfo(db, root, "Root page", System.out);
            buf.unfix(root, TEST_OWNER);

            P leaf = tree.getPage(keyProto.parse("1"), TEST_OWNER);
            printPageInfo(db, leaf, "Leaf page", System.out);
            buf.unfix(leaf, TEST_OWNER);
        } catch (UnsupportedOperationException e) {
            System.out.println("Unsupported operation: " + e.getMessage());
        }
    }

    public void testInsertDelete() {
        showTestName();
        Database<K, StringValue, P> db = createDatabase();

        int amount = 1000;
        Transaction<K, StringValue> tx = null;
        log.debug("Starting insert phase, version: " + db.getCommittedVersion());
        for (long i = 0; i < amount;) {
            tx = db.beginTransaction();
            if (i == 0) {
                assertTrue(db.getDatabaseTree().isEmpty(tx));
            }
            for (int c = 0; c < 10; c++, i++) {
                insert(tx, String.valueOf(i));
            }
            check(db);
            tx.commit();
        }
        check(db);

        tx = db.beginReadTransaction(db.getCommittedVersion());
        assertFalse(db.getDatabaseTree().isEmpty(tx));
        tx.commit();

        log.debug("Starting delete phase, version: " + db.getCommittedVersion());
        for (long i = 0; i < amount;) {
            tx = db.beginTransaction();
            for (int c = 0; c < 10; c++, i++) {
                delete(tx, String.valueOf(i));
            }
            check(db);
            tx.commit();
        }
        check(db);

        log.debug("Committed, version: " + db.getCommittedVersion());
        tx = db.beginReadTransaction(db.getCommittedVersion());
        assertTrue(db.getDatabaseTree().isEmpty(tx));
        tx.commit();
    }

    public void testGetRange() {
        showTestName();
        Database<K, StringValue, P> db = getFilledDB();
        Transaction<K, StringValue> tx = db.beginTransaction();

        assertEquals(EMPTY_LIST, tx.getRange(new KeyRangeImpl<K>(k(50), k(60))));
        assertEquals(EMPTY_LIST, tx.getRange(new KeyRangeImpl<K>(k(0), k(1))));

        checkContents(CollectionUtils.getList("1"), CollectionUtils.getPairSecondList(tx
            .getRange(new KeyRangeImpl<K>(k(0), k(2)))));

        checkContents(CollectionUtils.getList("1", "3", "4"), CollectionUtils
            .getPairSecondList(tx.getRange(new KeyRangeImpl<K>(k(0), k(5)))));

        checkContents(CollectionUtils.getList("20", "30"), CollectionUtils.getPairSecondList(tx
            .getRange(new KeyRangeImpl<K>(k(20), k(31)))));

        checkContents(CollectionUtils.getList("20", "30"), CollectionUtils.getPairSecondList(tx
            .getRange(new KeyRangeImpl<K>(k(16), k(1000)))));
        tx.commit();
    }

    public void testGetRangeExtensive() {
        showTestName();
        Database<K, StringValue, P> db = createDatabase();

        Transaction<K, StringValue> tx = db.beginTransaction();
        assertTrue(db.getDatabaseTree().isEmpty(tx));
        for (long i = 100; i > 1; i--) {
            insert(tx, String.valueOf(i));
        }
        tx.commit();

        tx = db.beginReadTransaction(db.getCommittedVersion());
        assertFalse(db.getDatabaseTree().isEmpty(tx));
        for (int s = 10; s < 20; s++) {
            for (int e = 50; e < 80; e++) {
                List<Pair<K, StringValue>> values = tx.getRange(new KeyRangeImpl<K>(k(s), k(e)));
                assertEquals(s + ", " + e, e - s, values.size());
                if (db.getDatabaseTree().isOrdered()) {
                    long c = s;
                    for (Pair<K, StringValue> val : values) {
                        assertEquals(String.valueOf(c), val.getSecond().getValue());
                        c++;
                    }
                } else {
                    List<StringValue> valList = CollectionUtils.getPairSecondList(values);
                    for (long c = s; c < e; c++) {
                        assertTrue("Looking for " + c + " from " + valList, valList
                            .contains(new StringValue(c)));
                    }
                }
            }
        }
        tx.commit();
    }

    protected boolean isDefaultClearUsed() {
        return true;
    }

    protected int getExpectedPageCountInSampleTree() {
        return 3;
    }

    protected int getEmptyPageCount() {
        // Default: storage contains the first space allocation map and the
        // tree info page (2 pages total)
        return 2;
    }

    public void testInsert() {
        showTestName();
        Database<K, StringValue, P> db = getFilledDB();
        Transaction<K, StringValue> tx = db.beginReadTransaction(db.getCommittedVersion());

        assertTrue(tx.contains(parse("3")));
        assertTrue(tx.contains(parse("1")));
        assertTrue(tx.contains(parse("10")));
        assertTrue(tx.contains(parse("20")));
        assertTrue(tx.contains(parse("4")));
        assertTrue(tx.contains(parse("15")));
        assertTrue(tx.contains(parse("30")));

        assertFalse(tx.contains(parse("0")));
        assertFalse(tx.contains(parse("2")));
        assertFalse(tx.contains(parse("5")));

        assertEquals("3", tx.get(parse("3")).getValue());
        assertEquals("1", tx.get(parse("1")).getValue());
        assertEquals("10", tx.get(parse("10")).getValue());

        assertNull(tx.get(parse("0")));
        assertNull(tx.get(parse("2")));
        assertNull(tx.get(parse("5")));
        tx.commit();
    }

    protected void checkDeletion(Transaction<K, StringValue> tx, String keyStr,
        String expectedValue) {
        K key = parse(keyStr);
        assertTrue(tx.contains(key));
        assertEquals(expectedValue, tx.get(key).getValue());
        assertTrue(tx.delete(key));
        assertFalse(tx.contains(key));
        assertNull(tx.get(key));
    }

    protected void checkInsertion(Transaction<K, StringValue> tx, String keyStr, String value) {
        K key = parse(keyStr);
        assertFalse(tx.contains(key));
        assertNull(tx.get(key));
        assertTrue(tx.insert(key, new StringValue(value)));
        assertTrue(tx.contains(key));
        assertEquals(value, tx.get(key).getValue());
    }

    public void testDelete() {
        showTestName();
        Database<K, StringValue, P> db = getFilledDB();
        Transaction<K, StringValue> tx = db.beginTransaction();
        checkDeletion(tx, "3", "3");
        checkDeletion(tx, "15", "15");
        checkDeletion(tx, "4", "4");

        checkInsertion(tx, "100", "sata");
        checkInsertion(tx, "3", "kolkki");

        checkDeletion(tx, "10", "10");
        checkDeletion(tx, "3", "kolkki");
    }

    public void testIsEmpty() {
        showTestName();
        Database<K, StringValue, P> db = createDatabase();

        Transaction<K, StringValue> tx = db.beginTransaction();
        assertTrue(db.getDatabaseTree().isEmpty(tx));
        checkInsertion(tx, "3", "3p");
        tx.commit();

        tx = db.beginReadTransaction(db.getCommittedVersion());
        assertFalse(db.getDatabaseTree().isEmpty(tx));
        tx.commit();

        tx = db.beginTransaction();
        checkInsertion(tx, "10", "x10");
        tx.commit();

        assertFalse(db.getDatabaseTree().isEmpty(tx));

        tx = db.beginTransaction();
        checkDeletion(tx, "10", "x10");
        tx.commit();

        assertFalse(db.getDatabaseTree().isEmpty(tx));

        tx = db.beginTransaction();
        checkDeletion(tx, "3", "3p");
        tx.commit();

        tx = db.beginReadTransaction(db.getCommittedVersion());
        assertTrue(db.getDatabaseTree().isEmpty(tx));
        tx.commit();
    }

    private void checkBufferFixes(PageBuffer buffer, int expected, String pos) {
        log.debug("Checking buffer fixes at " + pos);
        if (buffer == null)
            return;
        int fixes = buffer.getTotalPageFixes();
        assertEquals("Wrong amount of fixes at " + pos + ", ", expected, fixes);
    }

    private static final int BUF_FIX_COUNT = 200;

    public void testBufferFixes() {
        showTestName();
        Database<K, StringValue, P> db = createDatabase();
        PageBuffer buffer = db.getPageBuffer();
        if (buffer == null) {
            log.info("No page storage defined, skipping test");
            return;
        }

        // No page fixes as there is no root
        checkBufferFixes(db.getPageBuffer(), 0, "Beginning");

        Transaction<K, StringValue> tx = db.beginTransaction();
        assertTrue(db.getDatabaseTree().isEmpty(tx));

        for (int i = 0; i < BUF_FIX_COUNT; i++) {
            insert(tx, String.valueOf(i));
            checkBufferFixes(db.getPageBuffer(), getBufferFixesAfterActions(), "After insert "
                + i);
        }
        for (int i = 0; i < BUF_FIX_COUNT; i++) {
            checkBufferFixes(db.getPageBuffer(), getBufferFixesAfterActions(), "Before delete "
                + i);
            delete(tx, String.valueOf(i));
        }
        tx.commit();
        tx = db.beginReadTransaction(db.getCommittedVersion());
        assertTrue(db.getDatabaseTree().isEmpty(tx));
        tx.commit();

        db.getPageBuffer().printDebugInfo();

        db.requestMaintenance();

        // No page fixes as there is no root
        checkBufferFixes(db.getPageBuffer(), getBufferFixesAfterAllDeleted(), "End");
    }

    protected int getBufferFixesAfterActions() {
        return 1;
    }

    protected int getBufferFixesAfterAllDeleted() {
        return 0;
    }

    protected Iterable<Pair<K, StringValue>> getKeys(int start, int end, final K keyProto) {
        return new IteratorWrapper<Integer, Pair<K, StringValue>>(new IntegerRange(1, 1000)
            .iterator(), new Converter<Integer, Pair<K, StringValue>>() {
            private int counter = 0;

            @Override
            public Pair<K, StringValue> convert(Integer src) {
                K key = keyProto.fromInt(src);
                return new Pair<K, StringValue>(key, new StringValue(++counter));
            }
        });

    }

    public void testHistory() {
        showTestName();
        Database<K, StringValue, P> db = createDatabase();
        if (!db.isMultiVersion()) {
            // No test for non-multiversion databases
            return;
        }

        // No debugging messages for this test
        final Level orgTreePageLevel = Logger.getLogger(MVBTPage.class).getLevel();
        final Level orgPagePathLevel = Logger.getLogger(PagePath.class).getLevel();
        try {
            Logger.getLogger(MVBTPage.class).setLevel(Level.INFO);
            Logger.getLogger(PagePath.class).setLevel(Level.INFO);

            int version = 0;

            Collection<Integer> curContents = new ArrayList<Integer>();
            Map<Integer, Collection<Integer>> dbContents = new HashMap<Integer, Collection<Integer>>();

            for (Integer i : new IntegerRangeProvider(new IntegerRange(1, 25), new IntegerRange(
                90, 60), new IntegerRange(40, 55))) {

                Transaction<K, StringValue> tx = db.beginTransaction();
                tx.insert(keyProto.fromInt(i), new StringValue(i));
                tx.commit();

                version++;
                curContents.add(i);
                dbContents.put(version, new ArrayList<Integer>(curContents));
            }
            assertEquals(version, db.getMVDatabase().getCommittedVersion());
            doTestContents(db, dbContents);

            for (Integer i : new IntegerRangeProvider(new IntegerRange(5, 15), new IntegerRange(
                65, 75), new IntegerRange(55, 40))) {

                Transaction<K, StringValue> tx = db.beginTransaction();
                tx.delete(keyProto.fromInt(i));
                tx.commit();

                version++;
                assertTrue(curContents.remove(i));
                dbContents.put(version, new ArrayList<Integer>(curContents));
            }

            assertEquals(version, db.getMVDatabase().getCommittedVersion());
            doTestContents(db, dbContents);
        } finally {
            Logger.getLogger(MVBTPage.class).setLevel(orgTreePageLevel);
            Logger.getLogger(PagePath.class).setLevel(orgPagePathLevel);
        }
    }

    private void doTestContents(Database<K, StringValue, P> db,
        Map<Integer, Collection<Integer>> dbContents) {
        for (int v = 1; v <= db.getMVDatabase().getCommittedVersion(); v++) {
            Collection<Integer> expectedContents = dbContents.get(v);
            dummyTX.setReadVersion(v);

            for (Integer i : new IntegerRange(1, 95)) {
                MVTree<K, StringValue, ?> tree = db.getMVDatabase().getDatabaseTree();

                K key = keyProto.fromInt(i);
                if (expectedContents.contains(i)) {
                    assertTrue("Key " + key + " not found", tree.contains(key, dummyTX));
                    assertEquals(String.valueOf(i), tree.get(key, dummyTX).getValue());
                } else {
                    assertFalse("Should not contain " + i, tree.contains(key, dummyTX));
                    assertEquals(null, tree.get(key, dummyTX));
                }
            }
        }
    }
}
