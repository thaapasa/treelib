package fi.hut.cs.treelib.operations;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class ExactQueryOperation<K extends Key<K>> implements Operation<K> {

    private static boolean WARN_IF_NOT_FOUND = false;
    private static final Logger log = Logger.getLogger(ExactQueryOperation.class);

    private MDDatabase<K, IntegerValue, ?> database;

    public ExactQueryOperation(MDDatabase<K, IntegerValue, ?> database,
        StatisticsLogger statisticsLogger) {
        this.database = database;
    }

    private class ValueChecker implements Callback<Pair<MBR<K>, IntegerValue>> {
        public boolean found = false;

        @Override
        public boolean callback(Pair<MBR<K>, IntegerValue> entry) {
            found = true;
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Iterable<K> keys) {
        log.info("Executing exact query operation");
        MDTransaction<K, IntegerValue> tx = database.beginTransaction();
        ProgressCounter count = new ProgressCounter("exact");
        ValueChecker checker = new ValueChecker();
        for (K key : keys) {
            count.advance();
            MBR<K> mbrK = (MBR<K>) key;
            checker.found = false;
            tx.getExact(mbrK, checker);
            if (WARN_IF_NOT_FOUND && !checker.found) {
                log.warn("No value found for key " + key);
            }
        }
        tx.commit();
    }

    @Override
    public boolean requiresKeys() {
        return true;
    }
}
