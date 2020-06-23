package fi.hut.cs.treelib.operations;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.IntegerValue;

public class InsertOperation<K extends Key<K>> implements Operation<K> {

    private static final Logger log = Logger.getLogger(InsertOperation.class);

    private OperationExecutor<K> executor;

    public InsertOperation(OperationExecutor<K> executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Iterable<K> keys) {
        log.info("Executing insert...");
        Database<K, IntegerValue, ?> database = executor.getDatabase();

        Transaction<K, IntegerValue> tx = database.beginTransaction();
        ProgressCounter count = new ProgressCounter("insert");
        for (K key : keys) {
            count.advance();
            IntegerValue value = new IntegerValue((int) count.getCount());
            boolean inserted = tx.insert(key, value);
            if (!inserted) {
                log.info("Did not insert key " + key + " (duplicate key?, position: "
                    + count.getCount() + ")");
            }
        }
        tx.commit();
    }

    @Override
    public boolean requiresKeys() {
        return true;
    }

}
