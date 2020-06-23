package fi.hut.cs.treelib.action;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;

public class BeginTransactionAction<K extends Key<K>, V extends PageValue<?>> implements
    Action<K, V> {

    private static final String LOG_ID = "b";

    public BeginTransactionAction() {
    }

    @Override
    public Transaction<K, V> perform(Database<K, V, ?> database, Transaction<K, V> transaction) {
        assert transaction == null;
        assert database != null;
        return database.beginTransaction();
    }

    @Override
    public String toString() {
        return "Action: begin transaction";
    }

    @Override
    public BeginTransactionAction<K, V> readFromLog(String str) {
        return new BeginTransactionAction<K, V>();
    }

    @Override
    public String writeToLog() {
        return getLogIdentifier();
    }

    @Override
    public String getLogIdentifier() {
        return LOG_ID;
    }

    public static String writeActionToLog() {
        return LOG_ID;
    }

    @Override
    public K getKey() {
        return null;
    }

}
