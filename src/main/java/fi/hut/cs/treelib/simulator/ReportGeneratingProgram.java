package fi.hut.cs.treelib.simulator;

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

public class ReportGeneratingProgram<K extends Key<K>, V extends PageValue<?>> implements Program {

    private final Database<K, V, ?> database;
    /** 0=current version; -x=current version-x; +x=version x */
    private final int ver;
    private final Counter foundEntries = new Counter();
    private final ActionWriter<K, V> writer;
    private KeyRange<K> range;

    private static final Logger log = Logger.getLogger(ReportGeneratingProgram.class);

    public ReportGeneratingProgram(Database<K, V, ?> database, int ver, KeyRange<K> range,
        ActionWriter<K, V> writer) {
        this.database = database;
        this.ver = ver;
        this.writer = writer;
        this.range = range;
    }

    public ReportGeneratingProgram(Database<K, V, ?> database, KeyRange<K> range,
        ActionWriter<K, V> writer) {
        this.database = database;
        this.ver = 0;
        this.writer = writer;
        this.range = range;
    }

    public long getEntryCount() {
        return foundEntries.getCount();
    }

    @Override
    public void run() {
        if (range == null)
            range = database.getKeyPrototype().getEntireRange();

        int qv = ver > 0 ? ver : database.getCommittedVersion() + ver;
        assert qv > 0;

        foundEntries.reset();

        // Begin TX (latest committedTX)
        Transaction<K, V> tx = database.beginReadTransaction(qv);
        if (ver == 0)
            writer.writeBeginRead();
        else
            writer.writeBeginRead(ver);

        // Range query
        log.info("Starting report generation of version " + qv + "; tx ID is "
            + tx.getTransactionID());
        tx.getRange(range, new Callback<Pair<K, V>>() {
            @Override
            public boolean callback(Pair<K, V> object) {
                // Processing ...
                foundEntries.advance();
                // True to continue search
                return true;
            }
        });
        writer.writeRangeQuery(range);

        // Commit
        tx.commit();
        writer.writeCommit();

        log.info("Report generation of version " + qv + " found " + foundEntries.getCount()
            + " objects");
    }
}
