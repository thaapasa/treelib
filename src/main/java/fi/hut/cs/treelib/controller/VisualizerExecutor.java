package fi.hut.cs.treelib.controller;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.action.Action;
import fi.hut.cs.treelib.action.ActionReader;
import fi.hut.cs.treelib.action.BeginTransactionAction;
import fi.hut.cs.treelib.action.CommitTransactionAction;
import fi.hut.cs.treelib.action.DeleteAction;
import fi.hut.cs.treelib.action.InsertAction;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Converter;
import fi.tuska.util.Pair;

/**
 * Executes a predefined set of actions. Used by the tree visualizer software
 * to pre-build (or build during the visualization phase) the tree.
 * 
 * @author thaapasa
 * 
 * @param <K> the key type used in the tree
 */
public abstract class VisualizerExecutor<K extends Key<K>, V extends PageValue<?>> implements
    VisualizerController<K> {

    protected static final String SEPARATOR = ";";

    private static final Logger log = Logger.getLogger(VisualizerExecutor.class);
    private final K keyPrototype;
    private final V valuePrototype;
    private final Database<K, V, ?> database;
    private final List<String> operationLog = new ArrayList<String>();
    private int operationCounter = 0;

    private Transaction<K, V> transaction;

    public VisualizerExecutor(Database<K, V, ?> database) {
        this.database = database;
        this.keyPrototype = database.getKeyPrototype();
        this.valuePrototype = database.getValuePrototype();
        this.transaction = null;
    }

    @Override
    public void begin() {
        executeAndLog(new BeginTransactionAction<K, V>());
    }

    @Override
    public void commit() {
        executeAndLog(new CommitTransactionAction<K, V>());
    }

    @SuppressWarnings("unchecked")
    public boolean insert(K key, String value) {
        return executeAndLog(new InsertAction<K, V>(key, (V) valuePrototype.parse(value)));
    }

    @Override
    public boolean delete(K key) {
        return executeAndLog(new DeleteAction<K, V>(key));
    }

    @Override
    public String query(K key) {
        V value = transaction.get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public String query(K key, int version) {
        Transaction<K, V> tx = database.beginReadTransaction(version);
        V res = tx.get(key);
        tx.commit();
        return res != null ? res.toString() : null;
    }

    @Override
    public List<Pair<K, String>> rangeQuery(KeyRange<K> range) {
        List<Pair<K, V>> res = transaction.getRange(range);
        return res != null ? CollectionUtils.convertList(res, RESULT_LIST_CONVERTER) : null;
    }

    @Override
    public List<Pair<K, String>> rangeQuery(KeyRange<K> range, int version) {
        Transaction<K, V> tx = database.beginReadTransaction(version);
        List<Pair<K, V>> res = transaction.getRange(range);
        tx.commit();
        return res != null ? CollectionUtils.convertList(res, RESULT_LIST_CONVERTER) : null;
    }

    private boolean executeAndLog(Action<K, V> action) {
        if (action == null) {
            return false;
        }
        transaction = action.perform(database, transaction);
        operationLog.add(action.writeToLog());
        return true;
    }

    @Override
    public void flush() {
        database.flush();
    }

    public String getDefaultValue(K key) {
        return String.valueOf(++operationCounter);
    }

    public List<String> getOperationLog() {
        return operationLog;
    }

    private final Converter<Pair<K, V>, Pair<K, String>> RESULT_LIST_CONVERTER = new Converter<Pair<K, V>, Pair<K, String>>() {
        @Override
        public Pair<K, String> convert(Pair<K, V> source) {
            return new Pair<K, String>(source.getFirst(), source.getSecond().toString());
        }
    };

    @Override
    public boolean isMultiVersion() {
        return database.isMultiVersion();
    }

    @Override
    public int getLatestVersion() {
        return database.getLatestVersion();
    }

    @Override
    public boolean isActiveTransaction() {
        return transaction != null;
    }

    public void execute(String operation) {
        ActionReader<K, V> ar = new ActionReader<K, V>(keyPrototype, valuePrototype);
        Action<K, V> action = ar.read(operation);
        if (action == null) {
            log.warn("Could not parse operation " + operation);
            return;
        }
        executeAndLog(action);
    }

    @Override
    public Database<K, ?, ?> getDatabase() {
        return database;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> overlapQuery(K key) {
        if (!database.isMultiDimension())
            throw new UnsupportedOperationException();
        MDTransaction<K, V> tx = (MDTransaction<K, V>) transaction;
        MBR<K> mbrK = (MBR<K>) key;
        List<Pair<MBR<K>, V>> valList = tx.getOverlapping(mbrK);
        List<String> result = new ArrayList<String>(valList.size());
        for (Pair<MBR<K>, V> v : valList) {
            result.add(v.getSecond().toString());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> exactQuery(K key) {
        if (!database.isMultiDimension())
            throw new UnsupportedOperationException();
        MDTransaction<K, V> tx = (MDTransaction<K, V>) transaction;
        MBR<K> mbrK = (MBR<K>) key;
        List<V> valList = tx.getExact(mbrK);
        List<String> result = new ArrayList<String>(valList.size());
        for (V v : valList) {
            result.add(v.toString());
        }
        return result;
    }

}
