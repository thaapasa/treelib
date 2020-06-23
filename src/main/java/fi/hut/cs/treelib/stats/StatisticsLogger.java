package fi.hut.cs.treelib.stats;

import fi.hut.cs.treelib.stats.Statistics.Action;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.hut.cs.treelib.stats.Statistics.Value;

public interface StatisticsLogger {

    /**
     * Starts the statistics logging. This method starts the time measurement.
     */
    void startStatistics();

    void pauseStatistics();

    void continueStatistics();

    boolean isStarted();

    /**
     * Called to start a new logical action from the tree implementations. For
     * example, each insert() or get() method call automatically generates a
     * call to this method.
     * 
     * This call terminates the current action and starts a new one.
     */
    void newAction(Action action);

    /**
     * Log a single operation. Called from wherever the operations are
     * performed. This is logged as being part of the current action (started
     * from newAction).
     */
    void log(Operation operation);

    /**
     * Un-logs a single marking from the log.
     */
    void unlog(Operation operation);

    /**
     * Log a global operation. These are calculated separately and not reset
     * for each new action.
     */
    void log(GlobalOperation operation);

    /**
     * Log multiple operations. Can be used to track different amounts. This
     * is logged as being part of the current action (started from newAction).
     */
    void log(Value valueType, double value);

    /**
     * Returns the statistics object that contains the logged statistics. This
     * call terminates the current action (if one is active). This call also
     * (re-)calculates the endpoint of time measurement.
     * 
     * @return the statistics information object. This can be null, if
     * statistics logging is turned off (by using the NoStatistics class).
     */
    Statistics getStatistics();

    /**
     * Clears the statistics.
     */
    void clear();

}
