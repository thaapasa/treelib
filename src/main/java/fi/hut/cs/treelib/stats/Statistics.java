package fi.hut.cs.treelib.stats;

import java.util.Date;

public interface Statistics {

    enum Action {
        ACTION_INSERT, ACTION_DELETE, ACTION_QUERY, ACTION_QUERY_FLOOR, ACTION_QUERY_NEXT,
        ACTION_QUERY_EXACT, ACTION_QUERY_OVERLAPS, ACTION_CONTAINS, ACTION_RANGE_QUERY,
        ACTION_CLEAR, ACTION_SPECIAL
    };

    enum Operation {
        OP_QUERY_FOUND_OBJECT, OP_OBJECT_DELETED, OP_OBJECT_INSERTED,

        OP_SPACEMAP_ALLOCATE, OP_SPACEMAP_FREE, OP_SPACEMAP_CLEAR, OP_BUFFER_FIX, OP_BUFFER_READ,
        OP_BUFFER_WRITE, OP_PAGE_SPLIT, OP_PAGE_MERGE, OP_PAGE_REDISTRIBUTE, OP_VERSION_SPLIT,
        OP_KEY_SPLIT, OP_SPLIT_BY_X, OP_SPLIT_BY_Y, OP_SPLIT_BY_Z, OP_SPLIT_BY_OTHER,
        OP_TRAVERSE_PATH, OP_RETRAVERSE_PATH, OP_BACKTRACK_PATH, OP_SUBTREE_TRAVERSED,
        OP_KEYS_PROCESSED, OP_VERSION_STABLE, OP_VERSION_TRANSIENT,

        OP_MAINTENANCE_TX
    }

    enum GlobalOperation {
        GO_NEW_TRANSACTION, GO_MAINTENANCE_TX, GO_SEARCH_WRAPPED, GO_PAGE_SPLIT, GO_PAGE_MERGE,
        GO_PAGE_REDISTRIBUTE, GO_KEY_SPLIT, GO_VERSION_SPLIT
    }

    enum Value {
        VAL_SPLIT_RATIO, VAL_SPLIT_OVERLAP_AREA, VAL_SPLIT_OVERLAP_RATIO, VAL_PAGE_FILL_RATIO,
        VAL_LEAF_PAGE_FILL_RATIO, VAL_INDEX_PAGE_FILL_RATIO
    }

    long getActionCount();

    long getActionCount(Action action);

    boolean hasOperationStatistics(Operation operation);

    double getOperationMin(Operation operation);

    double getOperationMax(Operation operation);

    /**
     * @return the average value (mean) of the measured operation
     */
    double getOperationAverage(Operation operation);

    /**
     * @return the standard deviation of the measured operation
     */
    double getOperationDeviation(Operation operation);

    boolean hasGlobalOperationStatistics(GlobalOperation operation);

    long getGlobalOperationCount(GlobalOperation operation);

    boolean hasValueStatistics(Value value);

    double getValueMin(Value value);

    double getValueMax(Value value);

    double getValueAverage(Value value);

    /**
     * @return the amount of different value recordings
     */
    long getValueCount(Value value);

    /**
     * The returned time is calculated from the initial call to
     * StatisticsLogger.startStatistics()
     * 
     * @return the elapsed time for all actions and operations, in seconds
     */
    double getElapsedTime();

    Date getStartTime();

    Date getEndTime();

}
