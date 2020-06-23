package fi.hut.cs.treelib.test;

import fi.hut.cs.treelib.Database;

public class TestHandler {

    private WorkloadCreator workloadCreator;
    private WorkloadExecutor workloadExecutor;

    public TestHandler(WorkloadCreator workloadCreator, WorkloadExecutor workloadExecutor) {
        this.workloadCreator = workloadCreator;
        this.workloadExecutor = workloadExecutor;
    }

    public void createTest(Test test, Database<?, ?, ?> db) {
        // Check that the state is created
        // File stateFile = StateHandler.getDBStateFile(db, test.getState());
        // assert stateFile != null;

        // Check that the workload file used to create this state exists,
        // create if it doesn't
        workloadCreator.createWorkload(test.getWorkload(), db);
    }

    public void runTest(Test test, TestState state, Database<?, ?, ?> db) {
        workloadExecutor.executeWorkload(test.getWorkload(), state, db, true);
    }

}
