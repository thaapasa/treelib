package fi.hut.cs.treelib.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.OrderedTransaction;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.action.ActionWriter;
import fi.hut.cs.treelib.data.Generator;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.test.Workload.Type;
import fi.tuska.util.NotImplementedException;
import fi.tuska.util.Pair;

public class WorkloadCreator {

    private static final Logger log = Logger.getLogger(WorkloadCreator.class);

    private StateHandler stateHandler;

    public WorkloadCreator(StateHandler stateHandler) {
        this.stateHandler = stateHandler;
    }

    public <K extends Key<K>, V extends PageValue<?>> void createWorkload(Workload workload,
        Database<K, V, ?> db) {
        File file = workload.getWorkloadFile();
        if (file.exists())
            return;

        TestState basedOn = workload.getBasedOnState();
        log.info("Creating " + workload + ", based on " + basedOn + ", using database "
            + db.getIdentifier());
        if (basedOn != null) {
            // Possibly recursive call. Please make sure that there are no
            // cyclic references in states and workloads...
            stateHandler.createState(basedOn, db);
            stateHandler.restoreState(basedOn, db);
        } else {
            stateHandler.clearState(db);
        }

        try {
            generateWorkload(workload, db);
        } catch (IOException e) {
            log.warn("Could not create workload file " + workload.getFilename() + ": " + e, e);
        }
    }

    private <K extends Key<K>, V extends PageValue<?>> void generateWorkload(Workload workload,
        final Database<K, V, ?> db) throws IOException {

        log.info("Creating worload " + workload.getFilename());
        FileOutputStream fos = new FileOutputStream(workload.getWorkloadFile());
        PrintStream ps = new PrintStream(fos);

        Generator<V> valueGenerator = getValueGenerator(db);
        Generator<K> keyGenerator = getKeyGenerator(db, workload);

        // Ready to begin workload generation
        workload.resetLimits();
        ActionWriter<K, V> writer = new ActionWriter<K, V>(ps);
        for (int i = 0; i < workload.getNumTransactions(); i++) {
            createTransaction(workload, db, keyGenerator, valueGenerator, writer);
        }

        ps.flush();
        ps.close();
        fos.close();
    }

    private <K extends Key<K>, V extends PageValue<?>> void createTransaction(Workload workload,
        Database<K, V, ?> db, Generator<K> keyGenerator, Generator<V> valueGenerator,
        ActionWriter<K, V> writer) {

        Type type = workload.selectNextType(db);
        assert type != null;
        if (type.equals(Type.INSERT)) {
            createInsertTransaction(workload, db, keyGenerator, valueGenerator, writer);
        } else if (type.equals(Type.DELETE)) {
            createDeleteTransaction(workload, db, keyGenerator, writer);
        } else if (type.equals(Type.RANGE_QUERY)) {
            createRangeQueryTransaction(workload, db, keyGenerator, writer);
        } else if (type.equals(Type.KEY_QUERY)) {
            createKeyQueryTransaction(workload, db, keyGenerator, writer);
        }
    }

    private <K extends Key<K>, V extends PageValue<?>> void createInsertTransaction(
        Workload workload, Database<K, V, ?> db, Generator<K> keyGenerator,
        Generator<V> valueGenerator, ActionWriter<K, V> writer) {

        Transaction<K, V> tx = db.beginTransaction();
        writer.writeBegin();
        for (int i = 0; i < workload.getTransactionLength(); i++) {
            K key = getKey(tx, keyGenerator, false, true);
            V value = valueGenerator.generate();

            tx.insert(key, value);
            writer.writeInsert(key, value);
        }
        tx.commit();
        writer.writeCommit();
    }

    private <K extends Key<K>, V extends PageValue<?>> void createDeleteTransaction(
        Workload workload, Database<K, V, ?> db, Generator<K> keyGenerator,
        ActionWriter<K, V> writer) {

        Transaction<K, V> tx = db.beginTransaction();
        writer.writeBegin();
        for (int i = 0; i < workload.getTransactionLength(); i++) {
            K key = getKey(tx, keyGenerator, true, false);
            if (key == null) {
                log.warn("No more keys left in database, cannot delete");
                throw new IllegalStateException("No more keys, cannot delete");
            }
            tx.delete(key);
            writer.writeDelete(key);
        }
        tx.commit();
        writer.writeCommit();
    }

    private <K extends Key<K>, V extends PageValue<?>> Transaction<K, V> beginAndLogReadTX(
        Workload workload, Database<K, V, ?> db, ActionWriter<K, V> writer) {
        Transaction<K, V> tx = null;
        int hVer = workload.getHistoricalVersions();
        if (hVer == 0) {
            tx = db.beginReadTransaction(db.getCommittedVersion());
            writer.writeBeginRead();
        } else {
            if (hVer > 0) {
                int qv = Math.min(Workload.RANDOM.nextInt(hVer), db.getCommittedVersion());
                tx = db.beginReadTransaction(qv);
                writer.writeBeginRead(qv);
            } else {
                int hist = -Math.min(Workload.RANDOM.nextInt(-hVer), db.getCommittedVersion());
                assert hist <= 0;
                int qv = db.getCommittedVersion() + hist;
                assert qv >= 0;

                tx = db.beginReadTransaction(qv);
                writer.writeBeginRead(hist);
            }
        }
        return tx;
    }

    private <K extends Key<K>, V extends PageValue<?>> void createRangeQueryTransaction(
        Workload workload, Database<K, V, ?> db, Generator<K> keyGenerator,
        ActionWriter<K, V> writer) {

        Transaction<K, V> tx = beginAndLogReadTX(workload, db, writer);
        for (int i = 0; i < workload.getTransactionLength(); i++) {
            K minKey = getKey(tx, keyGenerator, false, false);
            K size = workload.getRangeSize(db.getKeyPrototype());
            K maxKey = minKey.add(size);

            KeyRange<K> range = new KeyRangeImpl<K>(minKey, maxKey);
            writer.writeRangeQuery(range);
        }
        tx.commit();
        writer.writeCommit();
    }

    private <K extends Key<K>, V extends PageValue<?>> void createKeyQueryTransaction(
        Workload workload, Database<K, V, ?> db, Generator<K> keyGenerator,
        ActionWriter<K, V> writer) {

        Transaction<K, V> tx = beginAndLogReadTX(workload, db, writer);
        for (int i = 0; i < workload.getTransactionLength(); i++) {
            K key = getKey(tx, keyGenerator, true, false);
            writer.writeQuery(key);
        }
        tx.commit();
        writer.writeCommit();
    }

    private <K extends Key<K>, V extends PageValue<?>> K getKey(Transaction<K, V> tx,
        Generator<K> keyGenerator, boolean mustExist, boolean mustNotExist) {
        if (mustExist && mustNotExist)
            throw new IllegalArgumentException("Paradoxial arguments");

        K key = keyGenerator.generate();

        if (!mustExist && !mustNotExist) {
            // No restrictions, just return the key
            return key;
        }

        boolean inDB = tx.contains(key);
        if (mustExist) {
            if (inDB)
                return key;

            if (tx instanceof OrderedTransaction<?, ?>) {
                OrderedTransaction<K, V> otx = (OrderedTransaction<K, V>) tx;
                Pair<K, V> next = otx.nextEntry(key);
                if (next != null) {
                    return next.getFirst();
                }
                // No next key, try from beginning of database
                key = key.getMinKey();
                next = otx.nextEntry(key);
                if (next != null) {
                    return next.getFirst();
                } else {
                    log.warn("No keys in database, cannot return existing key");
                    return null;
                }
            } else {
                throw new IllegalArgumentException(
                    "Can't seek existing keys in a non-ordered database");
            }
        }
        if (mustNotExist) {
            while (inDB) {
                // Create another key, check if it is in DB
                key = keyGenerator.generate();
                inDB = tx.contains(key);
            }
            assert !inDB;
            return key;
        }

        throw new NotImplementedException();
    }

    private <V extends PageValue<?>> Generator<V> getValueGenerator(final Database<?, V, ?> db) {

        return new Generator<V>() {
            private final V proto = db.getValuePrototype();
            private int counter = 0;

            @Override
            @SuppressWarnings("unchecked")
            public V generate() {
                return (V) proto.parse(String.valueOf(++counter));
            }

            @Override
            public V getPrototype() {
                return proto;
            }
        };
    }

    private <K extends Key<K>> Generator<K> getKeyGenerator(final Database<K, ?, ?> db,
        final Workload workload) {

        return new Generator<K>() {
            private final K proto = db.getKeyPrototype();
            private final K bounds = workload.getKeyBounds().getBounds(proto);
            private final K minLimit = workload.getKeyBounds().getMinLimit(proto);
            private final K maxLimit = workload.getKeyBounds().getMaxLimit(proto);
            private final Random random = workload.getKeyRandom();

            @Override
            public K generate() {
                return bounds.random(minLimit, maxLimit, random);
            }

            @Override
            public K getPrototype() {
                return proto;
            }
        };
    }
}
