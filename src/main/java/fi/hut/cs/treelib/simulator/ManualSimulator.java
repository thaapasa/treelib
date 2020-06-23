package fi.hut.cs.treelib.simulator;

import java.util.Random;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.action.ActionWriter;
import fi.hut.cs.treelib.data.DataGenerator;
import fi.tuska.util.Range;
import fi.tuska.util.math.RandomUtils;

public class ManualSimulator<K extends Key<K>, V extends PageValue<?>> extends Simulator<K, V> {

    private static final Logger log = Logger.getLogger(ManualSimulator.class);

    private int stepCounter = 0;
    private final ActionWriter<K, V> nullWriter = new ActionWriter<K, V>();

    public ManualSimulator(Database<K, V, ?> database) {
        super(database);
    }

    @Override
    public void simulate() {
        // createDataset();
        runQueryUpdateTest();
        // runRangeQueries(0);
        // runHistoryQueries();
        // runRangeQueries(1000);
    }

    private static final double RANGE_TX_AMOUNT = 100;
    private static final double RANGE_SIZE = 0.1;

    protected void runRangeQueries(int prevVer) {
        for (int i = 0; i < RANGE_TX_AMOUNT; i++) {
            KeyRange<K> range = generator(1).getRange(RANGE_SIZE);
            Program report = new ReportGeneratingProgram<K, V>(database, -RandomUtils.getInt(0,
                prevVer), range, writer);
            report.run();
        }
    }

    private static final double QU_UPDATE_FREQ = 1;
    private static final int QU_TX_AMOUNT = 1000;
    private static final Range QU_TX_LENGTH = new Range(5, 6);

    protected void runQueryUpdateTest() {
        int updatesLeft = (int) Math.round(QU_TX_AMOUNT * QU_UPDATE_FREQ);

        for (int i = 0; i < QU_TX_AMOUNT; i++) {
            int rem = QU_TX_AMOUNT - i;
            boolean nextUpdate = RandomUtils.flip(QU_UPDATE_FREQ);

            if (updatesLeft == 0) {
                nextUpdate = false;
            } else if (rem <= updatesLeft) {
                nextUpdate = true;
            }

            if (nextUpdate) {
                updatesLeft--;
                // Updating TX
                if (RandomUtils.flip()) {
                    // Inserting TX
                    runRandomizedInsert(1, QU_TX_LENGTH);
                } else {
                    // Deleting TX
                    runDataDeletion(QU_TX_LENGTH);
                }
            } else {
                // Querying TX
                // runGetQueries(1, QU_TX_LENGTH, -RandomUtils.getInt(0,
                // 1000));
                runGetQueries(1, QU_TX_LENGTH, 0);
            }
        }
    }

    private static final double HISTORY_TX_AMOUNT = 100;
    private static final Range HISTORY_TX_LEN = new Range(20, 100);
    private static final Range HISTORY_TX_PREVVER = new Range(1, 7000);

    protected void runHistoryQueries() {
        for (int i = 0; i < HISTORY_TX_AMOUNT; i++) {
            runGetQueries(1, HISTORY_TX_LEN, (int) HISTORY_TX_PREVVER.random());
        }
    }

    private static final double DATASET_INS_FREQ = 0.8;
    private static final double DATASET_TX_AMOUNT = 2000;
    private static final Range DATASET_TX_LEN = new Range(20, 100);

    protected void createDataset() {
        for (int i = 0; i < DATASET_TX_AMOUNT; i++) {
            if (RandomUtils.flip(DATASET_INS_FREQ)) {
                // Insertion TX
                runRandomizedInsert(1, DATASET_TX_LEN);
            } else {
                // Deletion TX
                runDataDeletion(DATASET_TX_LEN);
            }
        }
    }

    private static final double MIXED_INS_FREQ = 0.12;
    private static final double MIXED_DEL_FREQ = 0.07;
    private static final double MIXED_RANGE_FREQ = 0.2;

    protected void runMixedTest() {
        // Insert data
        runRandomizedInsert(100, new Range(200, 1800));
        outputStatistics(true, "initial-insert");

        for (int i = 0; i < 1000; i++) {
            double d = Math.random();
            if ((d -= MIXED_INS_FREQ) < 0) {
                // Insert
                runRandomizedInsert(1, new Range(10, 100));
            } else if ((d -= MIXED_DEL_FREQ) < 0) {
                // Delete
                runDataDeletion(new Range(0, 55));
            } else if ((d -= MIXED_RANGE_FREQ) < 0) {
                // Range query
                runRangeQueries(1, 6);
            } else {
                // Single-key query
                runGetQueries(1, new Range(50, 150), 0);
            }
        }
        outputStatistics(true, "mix");
    }

    protected void runSmallTest() {
        // Insert data
        runRandomizedInsert(10, new Range(200, 1800));
        outputStatistics(true, "insert");

        // Run range queries
        // runRangeQueries(40, -1);
        // runRangeQueries(5, -1);
        // outputStatistics(true, "range-query-all");
        // runLatestRangeQuery();
        // outputStatistics(true, "range-query-latest");

        // Run get queries
        runGetQueries(40, new Range(50, 150), 0);
        outputStatistics(true, "key-query-all");

        // Delete data
        runDataRangeDeletion(50, 0.7, null);
        // runDataDeletion(5, 0.7);
        outputStatistics(true, "delete");

        // Run get queries
        runGetQueries(40, new Range(50, 150), 0);
        outputStatistics(true, "key-query-all");

        // Run range queries
        // runRangeQueries(40, 40);
        // runRangeQueries(5, 5);
        // outputStatistics(true, "range-query-some");
        // runLatestRangeQuery();
        // outputStatistics(true, "range-query-latest");
    }

    protected void runDataDeletion(Range actionsPerTX) {
        DataGenerator<K> keyGen = generator((int) actionsPerTX.random());
        step("delete");
        Program deleteProg = new DataDeletingProgram<K, V>(database, keyGen, writer);
        deleteProg.run();
    }

    protected void runDataRangeDeletion(int txAmount, double deleteRatio, Range actionsPerTX) {
        // Don't write the range query to log file
        long toDelete = 0;
        if (actionsPerTX != null) {
            toDelete = (long) actionsPerTX.random();
        } else {
            ReportGeneratingProgram<K, V> report = new ReportGeneratingProgram<K, V>(database,
                null, nullWriter);
            report.run();
            long entries = report.getEntryCount();
            toDelete = (long) (entries * deleteRatio);
        }
        long deletePerTX = toDelete / txAmount;

        for (int i = 0; i < txAmount; i++) {
            step("delete-range");
            Program delete = new DataRangeDeletingProgram<K, V>(database, deletePerTX, writer);
            delete.run();
        }
    }

    protected void runLatestRangeQuery() {
        Program report = new ReportGeneratingProgram<K, V>(database, null, writer);
        report.run();
    }

    protected void runRangeQueries(int txAmount, int considerVersions) {
        int verMax = database.getCommittedVersion();
        if (considerVersions > verMax)
            considerVersions = verMax;
        Random r = new Random();

        for (int i = 0; i < txAmount; i++) {
            step("report");
            int version = considerVersions > 0 ? r.nextInt(considerVersions) : 0;
            Program report = new ReportGeneratingProgram<K, V>(database, version, null, writer);
            report.run();
        }
    }

    protected DataGenerator<K> generator(int amount) {
        K keyProto = database.getKeyPrototype();
        DataGenerator<K> gen = new DataGenerator<K>(keyProto, keyProto.parse("0"), keyProto
            .parse("10000000"), amount);
        return gen;
    }

    protected void runGetQueries(int txAmount, Range actionsPerTX, int ver) {
        for (int i = 0; i < txAmount; i++) {
            step("query");
            int amount = (int) actionsPerTX.random();
            DataGenerator<K> gen = generator(amount);
            Program query = new DataQueryProgram<K, V>(database, gen, ver, writer);
            query.run();
        }
    }

    protected void runRandomizedInsert(int txAmount, Range actionsPerTX) {
        for (int i = 0; i < txAmount; i++) {
            step("insert");
            int amount = (int) actionsPerTX.random();
            DataGenerator<K> gen = generator(amount);
            Program insert = new DataInsertingProgram<K, V>(database, gen, valueGenerator, writer);
            insert.run();
        }
    }

    private void step(String title) {
        log.info("Step " + (++stepCounter) + ": " + title);
    }

    @Override
    public String toString() {
        return "Manual simulator";
    }

}
