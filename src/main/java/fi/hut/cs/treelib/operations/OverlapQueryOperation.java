package fi.hut.cs.treelib.operations;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.tuska.util.Callback;
import fi.tuska.util.Holder;
import fi.tuska.util.Pair;

public class OverlapQueryOperation<K extends Key<K>> implements Operation<K> {

    private static final Logger log = Logger.getLogger(OverlapQueryOperation.class);
    private static final Boolean WARN_IF_MISSING_ENTRIES = false;
    private MDDatabase<K, IntegerValue, ?> database;

    public OverlapQueryOperation(MDDatabase<K, IntegerValue, ?> database,
        StatisticsLogger statisticsLogger) {
        this.database = database;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Iterable<K> keys) {
        MDTransaction<K, IntegerValue> tx = database.beginTransaction();
        ProgressCounter count = new ProgressCounter("overlaps");
        for (K key : keys) {
            count.advance();
            MBR<K> mbrK = (MBR<K>) key;
            final Holder<Boolean> found = new Holder<Boolean>(Boolean.FALSE);
            tx.getOverlapping(mbrK, new Callback<Pair<MBR<K>, IntegerValue>>() {
                @Override
                public boolean callback(Pair<MBR<K>, IntegerValue> entry) {
                    // Just go through the entries
                    found.setValue(Boolean.TRUE);
                    return true;
                }
            });
            if (!found.getValue() && WARN_IF_MISSING_ENTRIES) {
                log.warn("No value found for " + key);
            }
        }
        tx.commit();
    }

    @Override
    public boolean requiresKeys() {
        return true;
    }
}
