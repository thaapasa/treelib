package fi.hut.cs.treelib.action;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;

public class DeleteAction<K extends Key<K>, V extends PageValue<?>> implements Action<K, V> {

    private static final String LOG_ID = "d";
    private final K key;

    public DeleteAction(K key) {
        this.key = key;
    }

    @Override
    public Transaction<K, V> perform(Database<K, V, ?> database, Transaction<K, V> transaction) {
        transaction.delete(key);
        return transaction;
    }

    @Override
    public String toString() {
        return "Action: delete " + key;
    }

    @Override
    public DeleteAction<K, V> readFromLog(String str) {
        String parts[] = str.split(LOG_SPLITTER);
        if (parts.length < 1)
            return null;
        K newKey = key.parse(parts[parts.length - 1]);
        return new DeleteAction<K, V>(newKey);
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
