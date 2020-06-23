package fi.hut.cs.treelib.test;

import java.io.File;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.stats.NoStatistics;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.StatisticsPrinter;
import fi.hut.cs.treelib.storage.FilePageStorage;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.tuska.util.file.FileUtils;

public class StateHandler {

    private static final Logger log = Logger.getLogger(StateHandler.class);
    private StatisticsPrinter statisticsPrinter;
    private WorkloadCreator workloadCreator;
    private WorkloadExecutor workloadExecutor;

    public StateHandler(StatisticsPrinter statisticsPrinter) {
        this.statisticsPrinter = statisticsPrinter;
    }

    public void setWorkloadHandlers(WorkloadCreator workloadCreator,
        WorkloadExecutor workloadExecutor) {
        this.workloadCreator = workloadCreator;
        this.workloadExecutor = workloadExecutor;
    }

    public void createState(TestState state, Database<?, ?, ?> db) {
        // Check that the workload file used to create this state exists
        workloadCreator.createWorkload(state.getWorkload(), db);

        File stateFile = getDBStateFile(state, db);
        // Check if this state has already been created
        if (stateFile.exists())
            return;

        TestState basedOn = state.getWorkload().getBasedOnState();
        if (basedOn != null) {
            // Recursively check that the state this state is based on is
            // created
            createState(basedOn, db);
        }
        log.info(String.format("Creating state %s for database %s", state.getName(), db
            .getIdentifier()));

        StatisticsLogger stats = db.getStatisticsLogger();
        stats.clear();
        stats.startStatistics();

        workloadExecutor.executeWorkload(state.getWorkload(), state.getWorkload()
            .getBasedOnState(), db, false);

        // Run maintenance at the end of the state creation
        db.requestMaintenance();

        stats = db.getStatisticsLogger();
        statisticsPrinter.showStatistics(stats, db, "create-state-" + state.getName());

        // Save the current state to backup file
        saveState(state, db);

        if (log.isInfoEnabled())
            log.info("Created state " + state.getName() + ", output " + stats + " to "
                + statisticsPrinter);
    }

    private File getDatabaseFile(Database<?, ?, ?> db) {
        PageStorage storage = db.getPageStorage();
        if (storage instanceof FilePageStorage) {
            FilePageStorage fs = (FilePageStorage) storage;
            return fs.getFile();
        }
        return null;
    }

    public File getDBStateFile(TestState state, Database<?, ?, ?> db) {
        File dbFile = getDatabaseFile(db);
        return new File(dbFile.getParent(), dbFile.getName() + "." + state.getName());

    }

    /**
     * Restores the database from the given DB state backup.
     */
    protected void restoreState(TestState state, Database<?, ?, ?> db) {
        StatisticsLogger stats = db.getStatisticsLogger();
        db.setStatisticsLogger(NoStatistics.instance());
        File dbFile = getDatabaseFile(db);
        if (dbFile == null) {
            log.warn("Cannot load DB state, DB not backed to disk");
            return;
        }
        File fromFile = getDBStateFile(state, db);
        db.close();
        FileUtils.copy(fromFile, dbFile);
        db.reopen();
        db.setStatisticsLogger(stats);
    }

    /**
     * Saves the current DB state as the given state.
     */
    protected void saveState(TestState state, Database<?, ?, ?> db) {
        StatisticsLogger stats = db.getStatisticsLogger();
        db.setStatisticsLogger(NoStatistics.instance());
        File dbFile = getDatabaseFile(db);
        if (dbFile == null) {
            log.warn("Cannot save DB state, DB not backed to disk");
            return;
        }
        File toFile = getDBStateFile(state, db);
        db.close();
        FileUtils.copy(dbFile, toFile);
        db.reopen();
        db.setStatisticsLogger(stats);
    }

    /**
     * Clears the database, deletes the DB file.
     */
    protected void clearState(Database<?, ?, ?> db) {
        StatisticsLogger stats = db.getStatisticsLogger();
        db.setStatisticsLogger(NoStatistics.instance());
        File dbFile = getDatabaseFile(db);
        if (dbFile == null) {
            log.warn("Cannot save DB state, DB not backed to disk");
            return;
        }
        db.close();
        boolean res = dbFile.delete();
        assert res;
        assert !dbFile.exists();

        db.reopen();
        db.setStatisticsLogger(stats);
    }

}
