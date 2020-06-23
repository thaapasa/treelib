package fi.hut.cs.treelib.operations;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.BulkLoadable;
import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.stats.Statistics.Action;
import fi.tuska.util.Converter;
import fi.tuska.util.IteratorWrapper;
import fi.tuska.util.Pair;

public class BulkLoadOperation<K extends Key<K>> implements Operation<K> {

    private static final Logger log = Logger.getLogger(BulkLoadOperation.class);

    private Database<K, IntegerValue, ?> database;

    public BulkLoadOperation(Database<K, IntegerValue, ?> database) {
        this.database = database;
    }

    /**
     * Value generator for the input keys
     */
    private Converter<K, Pair<K, IntegerValue>> valueGenerator = new Converter<K, Pair<K, IntegerValue>>() {
        private int value = 0;

        @Override
        public Pair<K, IntegerValue> convert(K src) {
            return new Pair<K, IntegerValue>(src, IntegerValue.PROTOTYPE.parse(String
                .valueOf(++value)));
        }
    };

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Iterable<K> keys) {
        log.info("Executing bulk load");

        if (database instanceof BulkLoadable) {
            log.info("Bulk-loading to a bulk-loadable db");
            database.getStatisticsLogger().newAction(Action.ACTION_SPECIAL);

            BulkLoadable<K, IntegerValue> db = (BulkLoadable<K, IntegerValue>) database;
            Transaction<K, IntegerValue> tx = database.beginTransaction();
            Iterable<Pair<K, IntegerValue>> keyValues = new IteratorWrapper<K, Pair<K, IntegerValue>>(
                keys, valueGenerator);
            db.bulkLoad(keyValues, tx);
            tx.commit();
            log.info("Bulk-load complete");
        } else {
            log.warn("Unrecognized database type, cannot bulk-load!");
        }
    }

    @Override
    public boolean requiresKeys() {
        return false;
    }
}
