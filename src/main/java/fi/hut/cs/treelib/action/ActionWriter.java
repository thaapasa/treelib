package fi.hut.cs.treelib.action;

import java.io.PrintStream;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.PageValue;

public class ActionWriter<K extends Key<K>, V extends PageValue<?>> {

    private PrintStream target;

    public ActionWriter() {
        this.target = null;
    }

    public ActionWriter(PrintStream target) {
        this.target = target;
    }

    public void setTarget(PrintStream target) {
        if (this.target != null)
            throw new IllegalStateException("Target already set");
        this.target = target;
    }

    public void close() {
        if (target == null)
            return;
        target.flush();
        target.close();
    }

    public void flush() {
        if (target == null)
            return;
        target.flush();
    }

    public void writeAction(Action<K, V> action) {
        if (target == null)
            return;
        target.println(action.writeToLog());
    }

    public void writeBegin() {
        if (target == null)
            return;
        target.println(BeginTransactionAction.writeActionToLog());
    }

    /**
     * Writes a log record for beginning a read TX for the given committed
     * version
     * 
     * @param readVersion the committed version to read
     */
    public void writeBeginRead(int readVersion) {
        if (target == null)
            return;
        target.println(BeginReadTransactionAction.writeActionToLog(readVersion));
    }

    /**
     * Writes a log record for beginning a read TX for the latest committed
     * version.
     */
    public void writeBeginRead() {
        if (target == null)
            return;
        target.println(BeginReadTransactionAction.writeActionToLog());
    }

    public void writeCommit() {
        if (target == null)
            return;
        target.println(CommitTransactionAction.writeActionToLog());
    }

    public void writeInsert(K key, V value) {
        if (target == null)
            return;
        target.println(InsertAction.writeActionToLog(key, value));
    }

    public void writeDelete(K key) {
        if (target == null)
            return;
        target.println(DeleteAction.writeActionToLog(key));
    }

    public void writeQuery(K key) {
        if (target == null)
            return;
        target.println(QueryAction.writeActionToLog(key));
    }

    public void writeRangeQuery(KeyRange<K> range, long limitCount) {
        if (target == null)
            return;
        target.println(RangeQueryAction.writeActionToLog(range, limitCount));
    }

    public void writeRangeQuery(KeyRange<K> range) {
        if (target == null)
            return;
        target.println(RangeQueryAction.writeActionToLog(range, null));
    }

    public void writeStatisticsAction(String title) {
        target.println(StatisticsShowAction.writeActionToLog(title));
    }

}
