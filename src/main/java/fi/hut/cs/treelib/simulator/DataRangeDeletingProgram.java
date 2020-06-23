package fi.hut.cs.treelib.simulator;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.action.ActionWriter;
import fi.tuska.util.Callback;
import fi.tuska.util.Counter;
import fi.tuska.util.Pair;

public class DataRangeDeletingProgram<K extends Key<K>, V extends PageValue<?>> implements
    Program {

    private final Database<K, V, ?> database;
    private final Long limitCount;
    private final ActionWriter<K, V> writer;

    private static final Logger log = Logger.getLogger(DataRangeDeletingProgram.class);

    public DataRangeDeletingProgram(Database<K, V, ?> database, Long limitCount,
        ActionWriter<K, V> writer) {
        this.database = database;
        this.limitCount = limitCount;
        this.writer = writer;
    }

    @Override
    public void run() {
        KeyRange<K> range = database.getKeyPrototype().getEntireRange();

        // Begin TX
        Transaction<K, V> tx = database.beginTransaction();
        writer.writeBegin();

        log.info("Starting to delete entries (limit: " + limitCount + "); tx ID is "
            + tx.getTransactionID());
        final List<K> tbd = new ArrayList<K>(limitCount != null ? limitCount.intValue() : 1000);

        final Counter c = new Counter();

        // Range query
        tx.getRange(range, new Callback<Pair<K, V>>() {
            @Override
            public boolean callback(Pair<K, V> object) {
                K key = object.getFirst();
                c.advance();
                tbd.add(key);

                return limitCount != null ? c.getCount() < limitCount : true;
            }
        });
        // Actually, don't write range query
        // Just write what keys were deleted
        // writer.writeRangeQuery(range, limitCount);

        log.info("Found " + tbd.size() + " objects to delete, starting to delete them");

        for (K key : tbd) {
            tx.delete(key);
            writer.writeDelete(key);
        }

        tx.commit();
        writer.writeCommit();
    }
}
