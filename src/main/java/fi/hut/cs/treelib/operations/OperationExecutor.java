package fi.hut.cs.treelib.operations;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.action.Action;
import fi.hut.cs.treelib.action.ActionReader;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.data.KeyReader;
import fi.hut.cs.treelib.stats.DefaultStatisticsPrinter;
import fi.hut.cs.treelib.stats.NoStatistics;
import fi.hut.cs.treelib.stats.StatisticsImpl;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.StatisticsPrinter;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.iterator.Iterables;
import fi.tuska.util.iterator.ReaderIterator;

/**
 * Reads commands from log files that either contain just keys or
 * pre-generated (or logged) action listings.
 * 
 * @author thaapasa
 * 
 * @param <K> the key type used in the database
 */
public class OperationExecutor<K extends Key<K>> implements InitializingBean {

    private static final Logger log = Logger.getLogger(OperationExecutor.class);

    public static final String BEAN_NAME = "operationExecutor";

    public static final String OPERATION_INSERT = "insert";
    public static final String OPERATION_INSPECT = "inspect";
    public static final String OPERATION_STATS = "stats";
    public static final String OPERATION_CHECK = "check";
    public static final String OPERATION_QUERY_OVERLAPS = "overlaps";
    public static final String OPERATION_QUERY_EXACT = "exact";
    public static final String OPERATION_DELETE = "delete";
    public static final String OPERATION_CHECK_DATA = "check-data";
    public static final String OPERATION_EXECUTE = "execute";
    public static final String OPERATION_BULK_LOAD = "bulk-load";

    public static final PrintStream STATS_TARGET = System.out;

    public ExecuteOperation<K> executeOp;

    private Database<K, IntegerValue, ?> database;
    private K keyPrototype;
    private StatisticsLogger statisticsLogger = NoStatistics.instance();
    private StatisticsPrinter statisticsPrinter = new DefaultStatisticsPrinter();

    private final Map<String, Operation<K>> operations = new HashMap<String, Operation<K>>();

    public OperationExecutor() {
        // Default constructor

    }

    /**
     * I guess it's okay that this class has bindings to Spring; shouldn't be
     * too big a problem...
     */
    @Override
    @SuppressWarnings("unchecked")
    public void afterPropertiesSet() throws Exception {
        log.info("Initializing Operation Executor");

        if (Configuration.instance().isShowStatistics()) {
            statisticsLogger = new StatisticsImpl();
            database.setStatisticsLogger(statisticsLogger);
        }

        executeOp = new ExecuteOperation<K>(this);

        operations.put(OPERATION_INSERT, new InsertOperation<K>(this));
        operations.put(OPERATION_DELETE, new DeleteOperation<K>(this));
        operations.put(OPERATION_INSPECT, new InspectOperation<K, IntegerValue>(database));
        operations.put(OPERATION_STATS, new ShowStatsOperation<K, IntegerValue>(database));
        operations.put(OPERATION_CHECK_DATA,
            new CheckDataOperation<K>(database, statisticsLogger));
        operations.put(OPERATION_EXECUTE, executeOp);
        operations.put(OPERATION_BULK_LOAD, new BulkLoadOperation<K>(database));
        operations.put(OPERATION_CHECK, new CheckOperation<K, IntegerValue>(database,
            statisticsLogger));

        // Multidimensional operations (exact query, overlap query)
        if (database.isMultiDimension()) {
            MDDatabase<K, IntegerValue, ?> mddb = (MDDatabase<K, IntegerValue, ?>) database;
            operations.put(OPERATION_QUERY_OVERLAPS, new OverlapQueryOperation<K>(mddb,
                statisticsLogger));
            operations.put(OPERATION_QUERY_EXACT, new ExactQueryOperation<K>(mddb,
                statisticsLogger));
        }
    }

    public void setDatabase(Database<K, IntegerValue, ?> database) {
        this.database = database;
    }

    @Required
    public Database<K, IntegerValue, ?> getDatabase() {
        return database;
    }

    public void setKeyPrototype(K keyPrototype) {
        this.keyPrototype = keyPrototype;
    }

    public K getKeyPrototype() {
        return keyPrototype;
    }

    public IntegerKey getValuePrototype() {
        return IntegerKey.PROTOTYPE;
    }

    public StatisticsLogger getStatisticsLogger() {
        return statisticsLogger;
    }

    public Long getParamLong(String[] params, int index) {
        if (params == null || params.length <= index)
            return null;
        try {
            return new Long(params[index]);
        } catch (NumberFormatException e) {
            log.warn("Could not parse " + params[index] + " as an operation limit");
            return null;
        }
    }

    public void execute(String operation, String keyFile) {
        log.info("Starting execution");
        log.info("Database is " + database);
        log.info("Buffer is " + database.getPageBuffer());

        operation = operation.toLowerCase();

        boolean ok = true;
        try {
            Operation<K> op = operations.get(operation);
            if (op == executeOp) {
                // Start statistics
                if (Configuration.instance().isShowStatistics()) {
                    statisticsLogger.startStatistics();
                }
                // Special case: execute a transaction log
                try {
                    executeOp.executeActions(getActionIterable(keyFile));
                } catch (RuntimeException e) {
                    log.error("Database operation failed with exception: " + e.getMessage(), e);
                    halt(true, false, false);
                }
            } else if (op != null) {
                // Simple operations, that read a key file
                if (op.requiresKeys()) {
                    if (keyFile == null) {
                        log.warn("Operation " + operation + " requires key file to be specified");
                        halt(false, false, false);
                    }
                }
                Long limit = Configuration.instance().getOperationsLimit();
                Long skip = Configuration.instance().getOperationsSkip();
                try {
                    Iterable<K> keys = getKeyIterable(keyFile, limit, skip);
                    // Start statistics
                    if (Configuration.instance().isShowStatistics()) {
                        statisticsLogger.startStatistics();
                    }
                    op.execute(keys);
                } catch (RuntimeException e) {
                    log.error("Database operation failed with exception: " + e.getMessage(), e);
                    halt(true, false, false);
                }
            } else {
                log.warn("Unknown operation " + operation);
                ok = false;
            }
        } catch (Throwable e) {
            log.warn("Error when running the program: " + e);
            e.printStackTrace(System.err);
            log.info("Saving current database state and shutting down");
            halt(true, false, false);
        }
        // Normal exit
        halt(ok, false, ok);
    }

    protected void halt(boolean showStats, boolean checkConsistency, boolean noErrors) {
        if (Configuration.instance().isShowStatistics() && showStats) {
            log.info("Execution complete, showing statistics");
            statisticsPrinter.showStatistics(statisticsLogger, database, "operation");
        } else {
            log.info("Execution complete");
        }

        database.flush();
        if (checkConsistency) {
            database.checkConsistency();
        }
        STATS_TARGET.println("database-size: " + database.getPageBuffer().getTotalPageCount());
        System.exit(noErrors ? 0 : 1);
    }

    private Iterable<K> getKeyIterable(String filename, Long limit, Long skip) {
        if (filename == null) {
            return null;
        }
        try {
            return new KeyReader<K>(new ReaderIterator(new FileReader(filename)), keyPrototype,
                limit, skip);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Iterable<Action<K, IntegerValue>> getActionIterable(String filename) {
        if (filename == null) {
            return null;
        }
        try {
            return Iterables.get(new ActionReader<K, IntegerValue>(keyPrototype,
                IntegerValue.PROTOTYPE).iterator(new ReaderIterator(new FileReader(filename))));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected void progress(String operation, long count) {
        STATS_TARGET.println(operation + ": " + count);
        STATS_TARGET.flush();
    }

    private static void showUsage() {
        System.out.println("Usage:");
        System.out.println("java " + OperationExecutor.class.getName()
            + " cfgfile operation [keyfile]");
        System.out.println("where:");
        System.out.println("- cfgfile is the configuration file, e.g. 'exec-btree.xml'");
        System.out.println("- operation is one of 'insert', 'query', 'delete', 'inspect'");
        System.exit(0);
    }

    public static void main(String[] args) {
        log.info("Initializing application context");
        if (args.length < 2) {
            showUsage();
        }
        String cfgPath = "classpath:" + args[0];
        ApplicationContext ctx = new ClassPathXmlApplicationContext(new String[] { cfgPath,
            "classpath:operation-executor.xml" });
        String[] params = args.length > 3 ? CollectionUtils.tailArray(args, 3) : null;
        if (params != null) {
            log.info("Params: " + CollectionUtils.toString(params));
            Configuration cfg = (Configuration) ctx.getBean(Configuration.BEAN_NAME);
            cfg.readConfiguration(params);
        }

        OperationExecutor<?> exec = (OperationExecutor<?>) ctx.getBean(BEAN_NAME);
        exec.execute(args[1], args.length > 2 ? args[2] : null);
    }

}
