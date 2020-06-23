package fi.hut.cs.treelib.test;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.StatisticsPrinter;
import fi.tuska.util.CollectionUtils;

public class TestRunner<K extends Key<K>, V extends PageValue<?>> implements InitializingBean {

    private static final String CONFIG_PATH = "classpath:test-run.xml";
    private static final String BEAN_NAME = "testRunner";

    private static final Logger log = Logger.getLogger(TestRunner.class);
    private StatisticsPrinter statisticsPrinter;
    private StatisticsLogger stats;

    private List<TestSet<K, V>> testSets;

    private StateHandler stateHandler;
    private TestHandler testHandler;
    private WorkloadCreator workloadCreator;
    private WorkloadExecutor workloadExecutor;

    public TestRunner() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.stateHandler = new StateHandler(statisticsPrinter);
        this.workloadExecutor = new WorkloadExecutor(stateHandler);
        this.workloadCreator = new WorkloadCreator(stateHandler);
        this.testHandler = new TestHandler(workloadCreator, workloadExecutor);
        this.stateHandler.setWorkloadHandlers(workloadCreator, workloadExecutor);
    }

    @Required
    public void setStatisticsLogger(StatisticsLogger stats) {
        this.stats = stats;
    }

    @Required
    public void setStatisticsPrinter(StatisticsPrinter statisticsPrinter) {
        this.statisticsPrinter = statisticsPrinter;
    }

    @Required
    public void setTestSet(List<TestSet<K, V>> testSets) {
        this.testSets = testSets;
    }

    public void initTests() {
        log.info("Initializing test sets");

        for (TestSet<K, V> testSet : testSets) {
            Map<String, Database<K, V, ?>> databases = testSet.getDatabases();
            for (Database<K, V, ?> db : CollectionUtils.values(databases.entrySet())) {
                db.setStatisticsLogger(stats);
            }

            for (TestState state : testSet.getStates()) {
                log.info("Processing state " + state.getName());
                for (Database<K, V, ?> db : CollectionUtils.values(databases.entrySet())) {
                    log.debug("Checking state " + state.getName() + " for database " + db);
                    stateHandler.createState(state, db);
                }
            }

            for (Test test : testSet.getTests()) {
                log.info("Processing test " + test.getName());
                Database<K, V, ?> db = databases.entrySet().iterator().next().getValue();
                assert db != null;
                testHandler.createTest(test, db);
            }
        }
    }

    public void runTests() {
        initTests();
        log.info("Running tests, stats logger is " + stats);

        for (TestSet<K, V> testSet : testSets) {
            Map<String, Database<K, V, ?>> databases = testSet.getDatabases();
            for (Database<K, V, ?> db : CollectionUtils.values(databases.entrySet())) {
                db.setStatisticsLogger(stats);
            }

            for (Entry<String, Database<K, V, ?>> dbe : databases.entrySet()) {
                Database<K, V, ?> db = dbe.getValue();
                initializeDBForTests(db, dbe.getKey(), testSet);
                for (Test test : testSet.getTests()) {
                    for (TestState state : testSet.getStates()) {
                        String testName = test.getName(state);
                        if (log.isInfoEnabled())
                            log.info("=== Running test === " + testName + "@" + state + " for "
                                + db.getIdentifier() + " =====================");

                        testHandler.runTest(test, state, db);
                        // Run maintenance at the end of the test
                        db.requestMaintenance();

                        assert stats == db.getStatisticsLogger();
                        statisticsPrinter.setExtraTestInfo(test.getExtra());
                        statisticsPrinter.showStatistics(stats, db, testName);
                        if (log.isInfoEnabled())
                            log.info("Test completed, output " + stats + " to "
                                + statisticsPrinter);
                    }
                }
            }
        }
    }

    private void initializeDBForTests(Database<K, V, ?> db, String dbKey, TestSet<K, V> testSet) {
        String settings = testSet.getDatabaseSettings(dbKey);
        if (settings != null) {
            log.info("Setting configuration for DB " + dbKey + ": " + settings);
            Configuration.instance().readConfiguration(settings);
        }
        db.reinit();
    }

    private static void usage(PrintStream target) {
        target.println("Usage: TestRunner cmd");
        target.println("Where cmd is either init or run");
    }

    public static void main(String[] args) {
        log.info("Initializing application context");
        ApplicationContext ctx = new ClassPathXmlApplicationContext(new String[] { CONFIG_PATH });
        TestRunner<?, ?> runner = (TestRunner<?, ?>) ctx.getBean(BEAN_NAME);

        if (args.length < 1) {
            usage(System.out);
            return;
        }
        if ("init".equalsIgnoreCase(args[0])) {
            runner.initTests();
        } else if ("run".equalsIgnoreCase(args[0])) {
            runner.runTests();
        } else {
            usage(System.out);
        }
    }

}
