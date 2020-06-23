package fi.hut.cs.treelib.data;

import java.util.Iterator;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.action.Action;
import fi.hut.cs.treelib.operations.ProgressCounter;
import fi.tuska.util.iterator.Iterables;

/**
 * An executor class that executes a set of Actions, updating the active
 * transaction on the way.
 * 
 * @author thaapasa
 */
public class ActionExecutor<K extends Key<K>, V extends PageValue<?>> {

    private Database<K, V, ?> database;

    public ActionExecutor(Database<K, V, ?> database) {
        this.database = database;
    }

    public void execute(Iterable<Action<K, V>> iterable, ProgressCounter counter) {
        Transaction<K, V> tx = null;
        for (Action<K, V> action : iterable) {
            tx = action.perform(database, tx);

            if (counter != null) {
                counter.advance();
            }
        }
    }

    public void execute(Iterator<Action<K, V>> iterator, ProgressCounter counter) {
        execute(Iterables.get(iterator), counter);
    }

}
