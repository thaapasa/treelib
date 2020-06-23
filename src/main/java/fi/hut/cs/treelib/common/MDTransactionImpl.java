package fi.hut.cs.treelib.common;

import java.util.ArrayList;
import java.util.List;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.MDPage;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Action;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class MDTransactionImpl<K extends Key<K>, V extends PageValue<?>, L extends Key<L>, P extends MDPage<K, V, L>>
    extends TransactionImpl<MBR<K>, V, P> implements MDTransaction<K, V> {

    private MDDatabase<K, V, P> database;

    public MDTransactionImpl(MDDatabase<K, V, P> database, int version, boolean readOnly) {
        super(database, version, readOnly);
        this.database = database;
    }

    @Override
    public boolean getExact(MBR<K> key, Callback<Pair<MBR<K>, V>> callback) {
        if (committed)
            throw new IllegalStateException("Already committed");
        database.getStatisticsLogger().newAction(Action.ACTION_QUERY_EXACT);
        boolean res = database.getDatabaseTree().getExact(key, callback, this);
        runAfterAction();
        return res;
    }

    @Override
    public List<V> getExact(MBR<K> key) {
        if (committed)
            throw new IllegalStateException("Already committed");
        final StatisticsLogger stats = database.getStatisticsLogger();
        stats.newAction(Action.ACTION_QUERY_EXACT);
        final List<V> list = new ArrayList<V>();
        database.getDatabaseTree().getExact(key, new Callback<Pair<MBR<K>, V>>() {
            @Override
            public boolean callback(Pair<MBR<K>, V> object) {
                stats.log(Operation.OP_QUERY_FOUND_OBJECT);
                list.add(object.getSecond());
                // True to continue search
                return true;
            }
        }, this);

        runAfterAction();
        return list;
    }

    @Override
    public boolean getOverlapping(MBR<K> key, Callback<Pair<MBR<K>, V>> callback) {
        if (committed)
            throw new IllegalStateException("Already committed");
        database.getStatisticsLogger().newAction(Action.ACTION_QUERY_OVERLAPS);
        boolean res = database.getDatabaseTree().getOverlapping(key, callback, this);
        runAfterAction();
        return res;
    }

    @Override
    public List<Pair<MBR<K>, V>> getOverlapping(MBR<K> key) {
        if (committed)
            throw new IllegalStateException("Already committed");
        StatisticsLogger stats = database.getStatisticsLogger();
        stats.newAction(Action.ACTION_QUERY_OVERLAPS);
        ListCreatingCallback<Pair<MBR<K>, V>> lc = new ListCreatingCallback<Pair<MBR<K>, V>>(
            stats);
        database.getDatabaseTree().getOverlapping(key, lc, this);
        runAfterAction();
        return lc.getList();
    }

    @Override
    public boolean getAll(final Callback<Pair<MBR<K>, V>> callback) {
        return getOverlapping(database.getKeyPrototype().getEntireArea(), callback);
    }

    @Override
    public List<Pair<MBR<K>, V>> getAll() {
        return getOverlapping(database.getKeyPrototype().getEntireArea());
    }

}
