package fi.hut.cs.treelib.rtree;

import fi.hut.cs.treelib.AbstractMDDatabaseTest;
import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;

public class RTreeDatabaseTest extends AbstractMDDatabaseTest<RTreePage<FloatKey, StringValue>> {

    public static final int PAGE_SIZE = 1000;
    private static final MBR<FloatKey> KEY_PROTO = new MBR<FloatKey>(2, FloatKey.PROTOTYPE, false);

    public RTreeDatabaseTest() {
        super();
    }

    @Override
    protected RTreeDatabase<FloatKey, StringValue> createDatabase(PageStorage storage) {
        return new RTreeDatabase<FloatKey, StringValue>(32, MD_SMO_POLICY, KEY_PROTO,
            StringValue.PROTOTYPE, storage);
    }

    @Override
    protected RTreeDatabase<FloatKey, StringValue> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    public void testInsertManySame() {
        boolean oLimit = Configuration.instance().isLimitPageSizes();
        boolean oRStar = Configuration.instance().isUseRStarSplit();
        Configuration.instance().setUseRStarSplit(true);
        Configuration.instance().setLimitPageSizes(false);
        doTestInsertManySame();
        Configuration.instance().setUseRStarSplit(false);
        doTestInsertManySame();

        Configuration.instance().setLimitPageSizes(oLimit);
        Configuration.instance().setUseRStarSplit(oRStar);
    }

    public void doTestInsertManySame() {
        RTreeDatabase<FloatKey, StringValue> db = createDatabase();
        execute(db, OPERATIONS);

        MDTransaction<FloatKey, StringValue> t = db.beginTransaction();
        MBR<FloatKey> mbr = getMBR(-3, 10, 1, 8);
        for (int i = 0; i < 5000; i++) {
            t.insert(mbr, new StringValue(String.valueOf(i)));
        }
        t.commit();
    }

}
