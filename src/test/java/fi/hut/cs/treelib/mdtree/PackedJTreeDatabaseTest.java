package fi.hut.cs.treelib.mdtree;

import fi.hut.cs.treelib.AbstractMDDatabaseTest;
import fi.hut.cs.treelib.Coordinate;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.util.MBRPredicate;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class PackedJTreeDatabaseTest extends
    AbstractMDDatabaseTest<OMDPage<FloatKey, StringValue, Coordinate<FloatKey>>> {

    private static final int PAGE_SIZE = 350;
    protected final int transactionID = -1;

    public PackedJTreeDatabaseTest() {
        super();
    }

    @Override
    protected PackedJTreeDatabase<FloatKey, StringValue> createDatabase(PageStorage storage) {
        PackedJTreeDatabase<FloatKey, StringValue> db = new PackedJTreeDatabase<FloatKey, StringValue>(
            32, PAGE_SIZE, MD_SMO_POLICY, KEY_PROTO, StringValue.PROTOTYPE, storage);

        // Bulk load the db with dummy keys 1-1000
        db.bulkLoad(getKeys(1, 1000, KEY_PROTO), dummyTX);
        PagePath<MBR<FloatKey>, StringValue, OMDPage<FloatKey, StringValue, Coordinate<FloatKey>>> path = new PagePath<MBR<FloatKey>, StringValue, OMDPage<FloatKey, StringValue, Coordinate<FloatKey>>>(
            true);

        for (Pair<MBR<FloatKey>, StringValue> key : getKeys(1, 1000, KEY_PROTO)) {
            boolean deleted = db.getDatabaseTree().delete(key.getFirst(), path, dummyTX);
            assertTrue("Null when deleting " + key, deleted);
        }
        db.getPageBuffer().unfix(path, dummyTX);
        return db;
    }

    @Override
    public void testCloseAndOpen() {
        // Skipped on Packed J-tree
    }

    @Override
    protected PackedJTreeDatabase<FloatKey, StringValue> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    public void testTraverseMDPages() {
        PackedJTreeDatabase<FloatKey, StringValue> db = createDatabase();
        execute(db, OPERATIONS);
        db.checkConsistency();

        db.traverseMDPages(new MBRPredicate<FloatKey>(),
            new Callback<Page<MBR<FloatKey>, StringValue>>() {
                @Override
                public boolean callback(Page<MBR<FloatKey>, StringValue> page) {
                    // true to continue search
                    return true;
                }
            }, TEST_OWNER);
        db.checkConsistency();
    }

    @Override
    public void testBufferFixes() {
        // Skip this test
    }

    @Override
    public int getExpectedPageCountInSampleTree() {
        // TODO: Check if this number is okay
        return 29;
    }

    @Override
    protected boolean isDefaultClearUsed() {
        return false;
    }

}
