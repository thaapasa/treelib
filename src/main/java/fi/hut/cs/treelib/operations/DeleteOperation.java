package fi.hut.cs.treelib.operations;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.IntegerValue;

public class DeleteOperation<K extends Key<K>> implements Operation<K> {

    private static final Logger log = Logger.getLogger(DeleteOperation.class);

    private static boolean EXPECT_DELETE_TO_SUCCEED = false;

    private OperationExecutor<K> executor;

    public DeleteOperation(OperationExecutor<K> executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Iterable<K> keys) {
        log.info("Executing delete...");
        Database<K, IntegerValue, ?> database = executor.getDatabase();

        Transaction<K, IntegerValue> tx = database.beginTransaction();
        ProgressCounter count = new ProgressCounter("delete");
        for (K key : keys) {
            count.advance();
            Object val = tx.delete(key);
            if (val == null && EXPECT_DELETE_TO_SUCCEED) {
                log.warn("No object deleted for key " + key + " (op: " + count.getCount() + ")");
                log.info("Running consistency check");
                database.checkConsistency();
            }
        }
        tx.commit();
    }

    @Override
    public boolean requiresKeys() {
        return true;
    }

}
