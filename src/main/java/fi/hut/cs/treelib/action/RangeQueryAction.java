package fi.hut.cs.treelib.action;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.tuska.util.Callback;
import fi.tuska.util.Counter;
import fi.tuska.util.Pair;

public class RangeQueryAction<K extends Key<K>, V extends PageValue<?>> implements Action<K, V> {

    private static final String LOG_ID = "r";

    private final KeyRange<K> range;
    private final Long limitCount;

    public RangeQueryAction(K min, K max) {
        this.range = new KeyRangeImpl<K>(min, max);
        this.limitCount = null;
    }

    public RangeQueryAction(KeyRange<K> range) {
        this.range = range;
        this.limitCount = null;
    }

    public RangeQueryAction(K min, K max, long limitCount) {
        this.range = new KeyRangeImpl<K>(min, max);
        this.limitCount = limitCount;
    }

    public Long getLimitCount() {
        return limitCount;
    }

    @Override
    public Transaction<K, V> perform(Database<K, V, ?> database, Transaction<K, V> transaction) {
        if (limitCount != null) {
            final Counter c = new Counter();
            transaction.getRange(range, new Callback<Pair<K, V>>() {
                @Override
                public boolean callback(Pair<K, V> object) {
                    c.advance();
                    return c.getCount() < limitCount;
                }
            });
        } else {
            transaction.getRange(range);
        }
        return transaction;
    }

    public KeyRange<K> getKeyRange() {
        return range;
    }

    @Override
    public String toString() {
        return "Action: range query " + range;
    }

    @Override
    public RangeQueryAction<K, V> readFromLog(String str) {
        String parts[] = str.split(LOG_SPLITTER);
        if (parts.length < 1)
            return null;
        K min = range.getMin().parse(parts[1]);
        K max = range.getMin().parse(parts[2]);
        if (parts.length < 4)
            return new RangeQueryAction<K, V>(min, max);
        long limit = Long.parseLong(parts[3]);
        return new RangeQueryAction<K, V>(min, max, limit);
    }

    @Override
    public String writeToLog() {
        return writeActionToLog(range, limitCount);
    }

    public static <K extends Key<K>> String writeActionToLog(KeyRange<K> range, Long limitCount) {
        String line = LOG_ID + LOG_SPLITTER + range.getMin().write() + LOG_SPLITTER
            + range.getMax().write();

        return (limitCount != null) ? line + LOG_SPLITTER + limitCount : line;
    }

    @Override
    public String getLogIdentifier() {
        return LOG_ID;
    }

    @Override
    public K getKey() {
        return range.getMin();
    }

}
