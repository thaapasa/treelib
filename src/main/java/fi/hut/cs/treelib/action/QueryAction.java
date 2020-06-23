package fi.hut.cs.treelib.action;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;

public class QueryAction<K extends Key<K>, V extends PageValue<?>> implements Action<K, V> {

    private static final String LOG_ID = "q";

    private final K key;
    private V retrievedValue;
    private static boolean REQUIRE_KEY_FOUND = false;

    public QueryAction(K key) {
        this.key = key;
        this.retrievedValue = null;
    }

    @Override
    public Transaction<K, V> perform(Database<K, V, ?> database, Transaction<K, V> transaction) {
        retrievedValue = transaction.get(key);

        if (retrievedValue == null && REQUIRE_KEY_FOUND) {
            throw new RuntimeException("Key " + key + " not found from " + database + " @ "
                + transaction);
        }

        return transaction;
    }

    @Override
    public String toString() {
        return "Action: query " + key;
    }

    public V getRetrievedValue() {
        return retrievedValue;
    }

    @Override
    public QueryAction<K, V> readFromLog(String str) {
        String parts[] = str.split(LOG_SPLITTER);
        if (parts.length < 1)
            return null;
        K newKey = key.parse(parts[parts.length - 1]);
        return new QueryAction<K, V>(newKey);
    }

    @Override
    public String writeToLog() {
        return writeActionToLog(key);
    }

    public static <K extends Key<K>> String writeActionToLog(K key) {
        return LOG_ID + LOG_SPLITTER + key.write();
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
