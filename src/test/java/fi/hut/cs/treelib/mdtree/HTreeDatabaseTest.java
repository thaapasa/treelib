package fi.hut.cs.treelib.mdtree;

import fi.hut.cs.treelib.AbstractMDDatabaseTest;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.common.LongKey;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;

public class HTreeDatabaseTest extends
    AbstractMDDatabaseTest<OMDPage<FloatKey, StringValue, LongKey>> {

    private static final int PAGE_SIZE = 4096;
    private final MBR<FloatKey> defaultMBR = getMBR(-10, 220, -10, 220);

    @Override
    protected HTreeDatabase<FloatKey, StringValue> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    @Override
    protected HTreeDatabase<FloatKey, StringValue> createDatabase(PageStorage storage) {
        return createDatabase(defaultMBR, 10, storage);
    }

    protected HTreeDatabase<FloatKey, StringValue> createDatabase(MBR<FloatKey> dataRange,
        int hilbertOrder, PageStorage storage) {
        HTreeDatabase<FloatKey, StringValue> db = new HTreeDatabase<FloatKey, StringValue>(32,
            MD_SMO_POLICY, KEY_PROTO, StringValue.PROTOTYPE, storage);
        return db;
    }

    protected HTree<FloatKey, StringValue> createTree(MBR<FloatKey> dataRange, int hilbertOrder) {
        return createDatabase(dataRange, hilbertOrder, new MemoryPageStorage(PAGE_SIZE))
            .getDatabaseTree();
    }

    @Override
    protected boolean isDefaultClearUsed() {
        return false;
    }

}
