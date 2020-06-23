package fi.hut.cs.treelib.common;

import java.util.HashSet;
import java.util.Set;

import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.Callback;

public class CounterCallback<T> implements Callback<T> {

    private long count = 0;
    private final StatisticsLogger stats;
    private final boolean discreteItems;
    private final Set<T> discreteSet;

    public CounterCallback() {
        this(null, false);
    }

    public CounterCallback(boolean discreteItems) {
        this(null, discreteItems);
    }

    public CounterCallback(StatisticsLogger stats, boolean discreteItems) {
        this.stats = stats;
        this.discreteItems = discreteItems;
        this.discreteSet = discreteItems ? new HashSet<T>() : null;
    }

    @Override
    public boolean callback(T object) {
        if (stats != null)
            stats.log(Operation.OP_QUERY_FOUND_OBJECT);

        if (discreteItems) {
            if (discreteSet.contains(object))
                return true;
            discreteSet.add(object);
        }

        count++;
        return true;
    }

    public long getCount() {
        return count;
    }

}
