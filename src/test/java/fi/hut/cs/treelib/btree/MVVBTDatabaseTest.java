package fi.hut.cs.treelib.btree;

import fi.hut.cs.treelib.AbstractMVDatabaseTest;
import fi.hut.cs.treelib.MVDatabase;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;

public class MVVBTDatabaseTest extends
    AbstractMVDatabaseTest<IntegerKey, BTreePage<IntegerKey, StringValue>> {

    public MVVBTDatabaseTest() {
        super(IntegerKey.PROTOTYPE);
    }

    private static final int PAGE_SIZE = 400;

    @Override
    protected MVDatabase<IntegerKey, StringValue, BTreePage<IntegerKey, StringValue>> createDatabase() {
        return createDatabase(new MemoryPageStorage(PAGE_SIZE));
    }

    @Override
    protected MVDatabase<IntegerKey, StringValue, BTreePage<IntegerKey, StringValue>> createDatabase(
        PageStorage storage) {
        return new MVVBTDatabase<IntegerKey, StringValue>(32, nonThrashingPolicy,
            IntegerKey.PROTOTYPE, StringValue.PROTOTYPE, storage);
    }

    @Override
    protected boolean isTransactionSupported() {
        return true;
    }

    @Override
    protected boolean isOverlappingTransactionSupported() {
        return false;
    }

    @Override
    protected int getBufferFixesAfterAllDeleted() {
        // Root page (versioned data still there)
        return 1;
    }
}
