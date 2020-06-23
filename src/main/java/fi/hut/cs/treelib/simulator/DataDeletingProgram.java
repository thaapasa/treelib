package fi.hut.cs.treelib.simulator;

import java.util.Iterator;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.OrderedTransaction;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.action.ActionWriter;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.tuska.util.Counter;
import fi.tuska.util.Pair;
import fi.tuska.util.iterator.Iterables;

public class DataDeletingProgram<K extends Key<K>, V extends PageValue<?>> implements Program {

    private final Database<K, V, ?> database;
    private final ActionWriter<K, V> writer;
    private final Iterator<K> deleteKeys;

    private static final Logger log = Logger.getLogger(DataDeletingProgram.class);

    public DataDeletingProgram(Database<K, V, ?> database, Iterator<K> deleteKeys,
        ActionWriter<K, V> writer) {
        this.database = database;
        this.deleteKeys = deleteKeys;
        this.writer = writer;
    }

    @Override
    public void run() {

        // Begin TX
        OrderedTransaction<K, V> tx = (OrderedTransaction<K, V>) database.beginTransaction();

        writer.writeBegin();

        log.info("Starting to delete entries; tx is " + tx.getDebugID());
        final Counter c = new Counter();

        // Find an entry to delete
        for (K key : Iterables.get(deleteKeys)) {

            Pair<K, V> next = tx.nextEntry(key);
            if (next == null) {
                database.getStatisticsLogger().log(GlobalOperation.GO_SEARCH_WRAPPED);
                // Past the end, so find the first key
                next = tx.nextEntry(key.getMinKey());
                if (next == null) {
                    // Database is empty
                    log.warn("Database is empty, cannot delete anymore");
                    break;
                }
            }

            K foundKey = next.getFirst();
            assert foundKey != null;
            boolean res = tx.delete(foundKey);
            assert res;
            writer.writeDelete(foundKey);

            c.advance();
        }

        log.info("Deleted " + c.getCount() + " objects");

        tx.commit();
        writer.writeCommit();
    }
}
