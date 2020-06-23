package fi.hut.cs.treelib.simulator;

import java.util.Iterator;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.OrderedTransaction;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.action.ActionWriter;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.tuska.util.Pair;
import fi.tuska.util.iterator.Iterables;

/**
 * A simple program that runs queries to the database.
 * 
 * @author thaapasa
 */
public class DataQueryProgram<K extends Key<K>, V extends PageValue<?>> implements Program {

    private static final Logger log = Logger.getLogger(DataQueryProgram.class);

    private final Database<K, V, ?> database;
    /** 0=current version; -x=current version-x; +x=version x */
    private final int ver;
    private final Iterator<K> keySource;
    private final ActionWriter<K, V> writer;

    public DataQueryProgram(Database<K, V, ?> database, Iterator<K> keySource, int ver,
        ActionWriter<K, V> writer) {
        this.database = database;
        this.keySource = keySource;
        this.ver = ver;
        this.writer = writer;
    }

    @Override
    public void run() {
        // Begin TX (given version)

        int qv = ver > 0 ? ver : database.getCommittedVersion() + ver;
        assert qv > 0;

        OrderedTransaction<K, V> tx = (OrderedTransaction<K, V>) database
            .beginReadTransaction(qv);

        if (ver == 0)
            writer.writeBeginRead();
        else
            writer.writeBeginRead(ver);

        log.info("Starting to query entries, tx ID is " + tx.getDebugID());

        long c = 0;
        for (K key : Iterables.get(keySource)) {
            Pair<K, V> next = tx.nextEntry(key);
            if (next == null) {
                database.getStatisticsLogger().log(GlobalOperation.GO_SEARCH_WRAPPED);
                // Past the end, so find the first key
                next = tx.nextEntry(key.getMinKey());
                assert next != null;
            }
            K foundKey = next.getFirst();
            assert foundKey != null;
            // Convert the nextEntry query to a regular single-key query
            writer.writeQuery(foundKey);
            c++;
        }
        log.info("Queried " + c + " objects");

        // Commit TX
        tx.commit();
        writer.writeCommit();
    }

}
