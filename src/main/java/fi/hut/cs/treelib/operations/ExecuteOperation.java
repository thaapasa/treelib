package fi.hut.cs.treelib.operations;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.action.Action;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.data.ActionExecutor;

public class ExecuteOperation<K extends Key<K>> implements Operation<K> {

    private Database<K, IntegerValue, ?> database;

    public ExecuteOperation(OperationExecutor<K> executor) {
        this.database = executor.getDatabase();
    }

    public void executeActions(Iterable<Action<K, IntegerValue>> actions) {
        ActionExecutor<K, IntegerValue> executor = new ActionExecutor<K, IntegerValue>(database);
        ProgressCounter count = new ProgressCounter("execute");
        executor.execute(actions, count);
    }

    @Override
    public void execute(Iterable<K> keys) {
        throw new UnsupportedOperationException(
            "This is a wrapper class, use the string-parameter version instead");
    }

    @Override
    public boolean requiresKeys() {
        return true;
    }

}
