package fi.hut.cs.treelib.common;

import java.util.ArrayList;
import java.util.List;

import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.Callback;

public class ListCreatingCallback<T> implements Callback<T> {

    private final List<T> list = new ArrayList<T>();
    private final StatisticsLogger stats;

    public ListCreatingCallback() {
        this.stats = null;
    }

    public ListCreatingCallback(StatisticsLogger stats) {
        this.stats = stats;
    }

    @Override
    public boolean callback(T object) {
        if (stats != null)
            stats.log(Operation.OP_QUERY_FOUND_OBJECT);

        list.add(object);
        return true;
    }

    public List<T> getList() {
        return list;
    }

}
