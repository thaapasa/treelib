package fi.hut.cs.treelib.simulator;

import java.util.Iterator;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.action.ActionWriter;
import fi.hut.cs.treelib.data.Generator;
import fi.tuska.util.Converter;
import fi.tuska.util.IntegerRangeProvider;
import fi.tuska.util.IteratorWrapper;
import fi.tuska.util.iterator.Iterables;

/**
 * A simple program that inserts new values to the database.
 * 
 * @author thaapasa
 */
public class DataInsertingProgram<K extends Key<K>, V extends PageValue<?>> implements Program {

    private static final Logger log = Logger.getLogger(DataInsertingProgram.class);

    private final Database<K, V, ?> database;
    private final Iterator<K> keySource;
    private final ActionWriter<K, V> writer;
    private final Generator<V> valueGenerator;

    public DataInsertingProgram(Database<K, V, ?> database, Iterator<K> keySource,
        Generator<V> valueGenerator, ActionWriter<K, V> writer) {
        this.database = database;
        this.keySource = keySource;
        this.writer = writer;
        this.valueGenerator = valueGenerator;
    }

    public DataInsertingProgram(Database<K, V, ?> database, IntegerRangeProvider keys,
        Generator<V> valueGenerator, ActionWriter<K, V> writer) {
        this(database, getKeysIterator(keys, database.getKeyPrototype()), valueGenerator, writer);
    }

    @Override
    public void run() {
        // Begin TX
        Transaction<K, V> tx = database.beginTransaction();
        writer.writeBegin();

        log.info("Starting to insert keys into database; tx ID is " + tx.getTransactionID());
        long c = 0;
        for (K key : Iterables.get(keySource)) {
            V value = valueGenerator.generate();

            // Insert new value
            tx.insert(key, value);
            writer.writeInsert(key, value);
            c++;
        }

        // Commit TX
        tx.commit();
        writer.writeCommit();

        log.info(c + " keys inserted to the database, tx committed with commit time "
            + tx.getCommitVersion());
    }

    /**
     * @return a wrapper than converts the integers given by the provider to
     * database keys
     */
    private static <K extends Key<K>> Iterator<K> getKeysIterator(IntegerRangeProvider irp,
        final K keyPrototype) {
        return new IteratorWrapper<Integer, K>(irp.iterator(), new Converter<Integer, K>() {
            @Override
            public K convert(Integer source) {
                return source != null ? keyPrototype.parse(source.toString()) : null;
            }
        });
    }

}
