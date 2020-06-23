package fi.hut.cs.treelib.action;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;

public class BeginReadTransactionAction<K extends Key<K>, V extends PageValue<?>> implements
    Action<K, V> {

    private static final Logger log = Logger.getLogger(BeginReadTransactionAction.class);
    private static final String LOG_ID = "br";
    private int readVersion;

    public BeginReadTransactionAction() {
        this.readVersion = 0;
    }

    public BeginReadTransactionAction(int readVersion) {
        this.readVersion = readVersion;
    }

    @Override
    public Transaction<K, V> perform(Database<K, V, ?> database, Transaction<K, V> transaction) {
        assert transaction == null;
        assert database != null;
        int qv = readVersion > 0 ? readVersion : database.getCommittedVersion() + readVersion;

        if (log.isDebugEnabled())
            log.warn("Begin reading TX of committed version " + qv);
        return database.beginReadTransaction(qv);
    }

    public int getReadVersion() {
        return readVersion;
    }

    @Override
    public String toString() {
        if (readVersion < 0) {
            return "Action: begin read-only transaction for the latest version";
        } else {
            return "Action: begin read-only transaction for version " + readVersion;
        }
    }

    @Override
    public BeginReadTransactionAction<K, V> readFromLog(String str) {
        String parts[] = str.split(LOG_SPLITTER);
        if (parts.length < 2) {
            return new BeginReadTransactionAction<K, V>();
        } else {
            return new BeginReadTransactionAction<K, V>(Integer.parseInt(parts[1]));
        }
    }

    @Override
    public String writeToLog() {
        return writeActionToLog(readVersion);
    }

    public static String writeActionToLog(int readVersion) {
        return LOG_ID + LOG_SPLITTER + readVersion;
    }

    public static String writeActionToLog() {
        return LOG_ID;
    }

    @Override
    public String getLogIdentifier() {
        return LOG_ID;
    }

    @Override
    public K getKey() {
        return null;
    }

}
