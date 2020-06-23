package fi.hut.cs.treelib.stats;

import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.util.MapUtils;
import fi.tuska.util.math.StandardDeviation;
import fi.tuska.util.time.Timer;

/**
 * A standard implementation of statistics.
 * 
 * @author thaapasa
 */
public class StatisticsImpl implements StatisticsLogger, Statistics {

    private static final Logger log = Logger.getLogger(StatisticsImpl.class);

    private EnumMap<Action, Long> actionCounts;
    private EnumMap<Operation, StandardDeviation> operationCounts;
    // private EnumMap<Operation, Long> operationCounts;
    // private EnumMap<Operation, Long> operationMin;
    // private EnumMap<Operation, Long> operationMax;
    protected EnumMap<Operation, Long> operationCurrent;
    private EnumMap<Value, SingleValueStats> values;
    private EnumMap<GlobalOperation, Long> globalOperations;

    private Action currentAction;
    private long actionCount = 0;
    private Timer timer;
    private Date startDate;

    public StatisticsImpl() {
        this.actionCounts = new EnumMap<Action, Long>(Action.class);
        this.operationCounts = new EnumMap<Operation, StandardDeviation>(Operation.class);
        // this.operationCounts = new EnumMap<Operation,
        // Long>(Operation.class);
        this.operationCurrent = new EnumMap<Operation, Long>(Operation.class);
        // this.operationMin = new EnumMap<Operation, Long>(Operation.class);
        // this.operationMax = new EnumMap<Operation, Long>(Operation.class);
        this.values = new EnumMap<Value, SingleValueStats>(Value.class);
        this.globalOperations = new EnumMap<GlobalOperation, Long>(GlobalOperation.class);
        this.timer = new Timer();

        clear();
    }

    @Override
    public void clear() {
        currentAction = null;
        actionCount = 0;
        startDate = null;

        timer.reset();
        actionCounts.clear();
        operationCounts.clear();
        operationCurrent.clear();
        // operationMin.clear();
        // operationMax.clear();
        values.clear();
        globalOperations.clear();

        for (Operation o : Operation.values()) {
            assert operationCounts.get(o) == null;
            operationCounts.put(o, new StandardDeviation());
        }
    }

    @Override
    public void startStatistics() {
        log.info("Starting statistics logging");
        timer.start();
        this.startDate = new Date();
    }

    @Override
    public boolean isStarted() {
        return timer.isStarted();
    }

    @Override
    public void pauseStatistics() {
        if (!timer.isStarted())
            throw new IllegalStateException();
        if (timer.isPaused())
            log.warn("Already paused");
        timer.pause();
    }

    @Override
    public void continueStatistics() {
        if (timer.isStopped()) {
            timer.reset();
            timer.start();
            return;
        }
        if (!timer.isStarted())
            throw new IllegalStateException();
        if (!timer.isPaused())
            log.warn("Not paused");
        timer.start();
    }

    @Override
    public void newAction(Action action) {
        if (!timer.isRunning())
            return;
        terminateAction();
        assert currentAction == null;
        this.currentAction = action;
        assert currentAction != null;
    }

    @Override
    public void log(Operation operation) {
        if (!timer.isRunning())
            return;
        MapUtils.increaseLongValue(operationCurrent, operation);
    }

    @Override
    public void log(GlobalOperation operation) {
        if (!timer.isRunning())
            return;
        MapUtils.increaseLongValue(globalOperations, operation);
    }

    @Override
    public void unlog(Operation operation) {
        if (!timer.isRunning())
            return;

        // Check that this operation has been logged during this action
        Long curVal = operationCurrent.get(operation);
        if (curVal == null || curVal < 1)
            throw new IllegalStateException("This operation has not been logged in this action");
        // Decrease the operation count by one
        MapUtils.increaseLongValue(operationCurrent, operation, -1);
    }

    @Override
    public void log(Value valueType, double value) {
        if (!timer.isRunning())
            return;
        log.debug("Logging " + valueType.toString().toLowerCase() + ": " + value);
        SingleValueStats stat = values.get(valueType);
        if (stat == null) {
            stat = new SingleValueStats();
            values.put(valueType, stat);
        }
        stat.log(value);
    }

    @Override
    public Statistics getStatistics() {
        if (!timer.isStarted()) {
            log.warn("Statistics logging not started");
            return null;
        }
        terminateAction();
        timer.stop();
        return this;
    }

    private void terminateAction() {
        if (currentAction == null)
            return;
        MapUtils.increaseLongValue(actionCounts, currentAction);
        actionCount++;

        for (Operation op : Operation.values()) {
            long val = getValue(operationCurrent, op);
            // Set minimum
            // Long curMin = operationMin.get(op);
            // if (curMin == null || val < curMin.longValue()) {
            // operationMin.put(op, val);
            // }
            // Set maximum
            // Long curMax = operationMax.get(op);
            // if (curMax == null || val > curMax.longValue()) {
            // operationMax.put(op, val);
            // }
            // Set totals
            // MapUtils.increaseLongValue(operationCounts, op, val);
            operationCounts.get(op).add(val);
        }
        operationCurrent.clear();
        currentAction = null;
    }

    @Override
    public long getActionCount() {
        return actionCount;
    }

    @Override
    public long getActionCount(Action action) {
        return getValue(actionCounts, action);
    }

    @Override
    public long getGlobalOperationCount(GlobalOperation operation) {
        return getValue(globalOperations, operation);
    }

    @Override
    public boolean hasOperationStatistics(Operation operation) {
        boolean statsFound = getOperationMax(operation) != 0.0d;
        if (!statsFound) {
            if (operation == Operation.OP_BUFFER_FIX || operation == Operation.OP_BUFFER_READ
                || operation == Operation.OP_BUFFER_WRITE) {
                statsFound = true;
            }
        }
        return statsFound;
    }

    @Override
    public boolean hasGlobalOperationStatistics(GlobalOperation operation) {
        return getGlobalOperationCount(operation) != 0;
    }

    /**
     * @return the average value (mean) of the measured operation
     */
    @Override
    public double getOperationAverage(Operation operation) {
        if (getActionCount() == 0)
            return 0;
        return operationCounts.get(operation).getMean();
    }

    /**
     * @return the standard deviation of the measured operation
     */
    @Override
    public double getOperationDeviation(Operation operation) {
        if (getActionCount() == 0)
            return 0;
        return operationCounts.get(operation).getDeviation();
    }

    @Override
    public double getOperationMax(Operation operation) {
        if (getActionCount() == 0)
            return 0;
        return operationCounts.get(operation).getMax();
    }

    @Override
    public double getOperationMin(Operation operation) {
        if (getActionCount() == 0)
            return 0;
        return operationCounts.get(operation).getMin();
    }

    private static <K> long getValue(Map<K, Long> map, K key) {
        Long v = map.get(key);
        return v != null ? v.longValue() : 0;
    }

    @Override
    public String toString() {
        return "Statistics logger implementation";
    }

    @Override
    public double getElapsedTime() {
        return timer.getElapsedSeconds();
    }

    @Override
    public boolean hasValueStatistics(Value value) {
        return values.containsKey(value);
    }

    @Override
    public double getValueAverage(Value value) {
        SingleValueStats stat = values.get(value);
        return stat != null ? stat.getAverage() : 0;
    }

    @Override
    public double getValueMax(Value value) {
        SingleValueStats stat = values.get(value);
        return stat != null ? stat.getMax() : 0;
    }

    @Override
    public double getValueMin(Value value) {
        SingleValueStats stat = values.get(value);
        return stat != null ? stat.getMin() : 0;
    }

    @Override
    public long getValueCount(Value value) {
        SingleValueStats stat = values.get(value);
        return stat != null ? stat.getCount() : 0;
    }

    @Override
    public Date getStartTime() {
        return startDate;
    }

    @Override
    public Date getEndTime() {
        return timer.getEndTime();
    }

}
