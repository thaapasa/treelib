package fi.hut.cs.treelib.test;

import java.io.File;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.action.ActionWriter;
import fi.hut.cs.treelib.simulator.ActionReaderProgram;
import fi.hut.cs.treelib.simulator.Program;
import fi.hut.cs.treelib.stats.StatisticsLogger;

public class WorkloadExecutor {

    private StateHandler stateHandler;

    public WorkloadExecutor(StateHandler stateHandler) {
        this.stateHandler = stateHandler;
    }

    /**
     * Execute the given workload at the given state.
     * 
     * @param <K>
     * @param <V>
     * @param db
     * @param state
     * @param workload
     */
    public <K extends Key<K>, V extends PageValue<?>> void executeWorkload(Workload workload,
        TestState state, Database<K, V, ?> db, boolean doStats) {

        if (state != null) {
            stateHandler.restoreState(state, db);
        } else {
            stateHandler.clearState(db);
        }

        File wlFile = workload.getWorkloadFile();
        assert wlFile.exists();

        // Actions are not logged
        ActionWriter<K, V> writeTarget = new ActionWriter<K, V>();
        Program p = new ActionReaderProgram<K, V>(db, wlFile, writeTarget);

        if (doStats) {
            StatisticsLogger stats = db.getStatisticsLogger();
            stats.clear();
            stats.startStatistics();
        }
        p.run();
    }

}
