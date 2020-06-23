package fi.hut.cs.treelib.mvbt;

import fi.hut.cs.treelib.AbstractMVDatabaseTest;
import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;

public class CMVBTDatabaseTest extends
    AbstractMVDatabaseTest<IntegerKey, MVBTPage<IntegerKey, StringValue>> {

    private static final int PAGE_SIZE = 350;

    public CMVBTDatabaseTest() {
        super(IntegerKey.PROTOTYPE);
    }

    @Override
    protected CMVBTDatabase<IntegerKey, StringValue> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    @Override
    protected CMVBTDatabase<IntegerKey, StringValue> createDatabase(PageStorage storage) {
        return new CMVBTDatabase<IntegerKey, StringValue>(40, 8, nonThrashingPolicy,
            nonThrashingPolicy, IntegerKey.PROTOTYPE, StringValue.PROTOTYPE, storage,
            new MemoryPageStorage(storage.getPageSize()));
    }

    @Override
    protected boolean isDefaultClearUsed() {
        return false;
    }

    @Override
    public void testBufferFixes() {
        showTestName();
        // Skip this test
    }

    public void testStableVer() {
        showTestName();
        PageStorage storage = new MemoryPageStorage(PAGE_SIZE);
        Configuration c = Configuration.instance();
        int mf = c.getMaintenanceFrequency();
        try {
            {
                CMVBTDatabase<IntegerKey, StringValue> db = createDatabase(storage);
                // This sets the global configuration, need to reset (see the
                // finally-clause)
                db.setAutoMaintenance(1000, 1000);

                Transaction<IntegerKey, StringValue> tx = db.beginTransaction();
                insert(tx, "1");
                insert(tx, "4");
                tx.commit();

                tx = db.beginTransaction();
                insert(tx, "5");
                delete(tx, "4");
                tx.commit();

                assertEquals(2, db.getCommittedVersion());
                assertFalse(db.getDatabaseTree().isStable(1));
                assertFalse(db.getDatabaseTree().isStable(2));
                assertTrue(db.getDatabaseTree().hasTransientCommits());
                db.flush();

                assertEquals(2, db.getCommittedVersion());
                assertTrue(db.getDatabaseTree().isStable(1));
                assertTrue(db.getDatabaseTree().isStable(2));
            }

            {
                CMVBTDatabase<IntegerKey, StringValue> db = createDatabase(storage);
                assertEquals(2, db.getCommittedVersion());
                assertTrue(db.getDatabaseTree().isStable(1));
                assertTrue(db.getDatabaseTree().isStable(2));
            }
        } finally {
            // Reset configuration
            c.setMaintenanceFrequency(mf);
        }
    }

}
