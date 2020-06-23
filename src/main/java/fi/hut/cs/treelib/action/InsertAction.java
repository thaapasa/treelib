package fi.hut.cs.treelib.action;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;

public class InsertAction<K extends Key<K>, V extends PageValue<?>> implements Action<K, V> {

    private static final String LOG_ID = "i";
    private final K key;
    private final V value;
    private static int newValueCounter = 0;

    public InsertAction(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public Transaction<K, V> perform(Database<K, V, ?> database, Transaction<K, V> transaction) {
        transaction.insert(key, value);
        return transaction;
    }

    @Override
    public String toString() {
        return "Action: insert " + key + ": " + value;
    }

    @SuppressWarnings("unchecked")
    private V getNewValue() {
        return (V) value.parse(String.valueOf(++newValueCounter));
    }

    @Override
    @SuppressWarnings("unchecked")
    public InsertAction<K, V> readFromLog(String str) {
        String parts[] = str.split(LOG_SPLITTER);
        if (parts.length < 2)
            return null;
        K newKey = null;
        V newValue = null;
        if (parts.length == 2) {
            // Just the key given
            newKey = key.parse(parts[1]);
            newValue = getNewValue();
        } else {
            // Key and value given
            newKey = key.parse(parts[parts.length - 2]);
            newValue = (V) value.parse(parts[parts.length - 1]);
        }
        return new InsertAction<K, V>(newKey, newValue);
    }

    @Override
    public String writeToLog() {
        return writeActionToLog(key, value);
    }

    public static <K extends Key<K>, V extends PageValue<?>> String writeActionToLog(K key,
        V value) {
        return LOG_ID + LOG_SPLITTER + key.write() + LOG_SPLITTER + value.write();
    }

    @Override
    public String getLogIdentifier() {
        return LOG_ID;
    }

    @Override
    public K getKey() {
        return key;
    }

}
