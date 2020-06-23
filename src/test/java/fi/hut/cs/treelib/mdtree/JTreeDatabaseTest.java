package fi.hut.cs.treelib.mdtree;

import fi.hut.cs.treelib.AbstractMDDatabaseTest;
import fi.hut.cs.treelib.Coordinate;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.util.MBRPredicate;
import fi.tuska.util.Callback;

public class JTreeDatabaseTest extends
    AbstractMDDatabaseTest<OMDPage<FloatKey, StringValue, Coordinate<FloatKey>>> {

    public static final int PAGE_SIZE = 350;

    public JTreeDatabaseTest() {
        super();
    }

    @Override
    protected JTreeDatabase<FloatKey, StringValue> createDatabase(PageStorage storage) {
        return new JTreeDatabase<FloatKey, StringValue>(32, MD_SMO_POLICY, KEY_PROTO,
            StringValue.PROTOTYPE, storage);
    }

    @Override
    protected JTreeDatabase<FloatKey, StringValue> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    public void testTraverseMDPages() {
        JTreeDatabase<FloatKey, StringValue> db = createDatabase();
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
    protected boolean isDefaultClearUsed() {
        return false;
    }

}
