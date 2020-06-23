package fi.hut.cs.treelib.stats;

import fi.hut.cs.treelib.stats.Statistics.Action;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.hut.cs.treelib.stats.Statistics.Value;

/**
 * Empty implementation of statistics.
 * 
 * @author thaapasa
 */
public class NoStatistics implements StatisticsLogger {

    private static final NoStatistics instance = new NoStatistics();

    private NoStatistics() {
    }

    public static StatisticsLogger instance() {
        return instance;
    }

    @Override
    public void log(Operation operation) {
    }

    @Override
    public void log(Value valueType, double value) {
    }

    @Override
    public void newAction(Action action) {
    }

    @Override
    public Statistics getStatistics() {
        return null;
    }

    @Override
    public String toString() {
        return "Disabled statistics logger";
    }

    @Override
    public void startStatistics() {
    }

    @Override
    public boolean isStarted() {
        return false;
    }

    @Override
    public void unlog(Operation operation) {
    }

    @Override
    public void clear() {
    }

    @Override
    public void log(GlobalOperation operation) {
        // TODO Auto-generated method stub

    }

    @Override
    public void continueStatistics() {
    }

    @Override
    public void pauseStatistics() {
    }

}
