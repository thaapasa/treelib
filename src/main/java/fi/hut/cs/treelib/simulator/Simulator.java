package fi.hut.cs.treelib.simulator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.action.ActionWriter;
import fi.hut.cs.treelib.data.Generator;
import fi.hut.cs.treelib.stats.StatisticsImpl;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.DefaultStatisticsPrinter;
import fi.hut.cs.treelib.stats.StatisticsPrinter;

public abstract class Simulator<K extends Key<K>, V extends PageValue<?>> {

    private static final Logger log = Logger.getLogger(Simulator.class);

    public static final String BEAN_NAME = "simulator";
    private static final String CFG_PATH = "classpath:simulator.xml";

    protected final Database<K, V, ?> database;
    protected final ActionWriter<K, V> writer = new ActionWriter<K, V>();
    protected final Generator<V> valueGenerator;
    protected final StatisticsLogger stats;
    protected final StatisticsPrinter statisticsPrinter = new DefaultStatisticsPrinter();
    private File logTarget;

    protected Simulator(final Database<K, V, ?> database) {
        this.database = database;
        this.stats = new StatisticsImpl();
        database.setStatisticsLogger(stats);
        valueGenerator = new Generator<V>() {
            private final V proto = database.getValuePrototype();
            private int counter = 0;

            @Override
            @SuppressWarnings("unchecked")
            public V generate() {
                return (V) proto.parse(String.valueOf(++counter));
            }

            @Override
            public V getPrototype() {
                return proto;
            }
        };
    }

    protected void outputStatistics(boolean logToFile, String title) {
        System.err.flush();
        if (!stats.isStarted())
            throw new IllegalStateException("Stats not started yet!");
        statisticsPrinter.showStatistics(stats, database, title);

        stats.clear();
        stats.startStatistics();

        if (logToFile) {
            writer.writeStatisticsAction(title);
        }
    }

    public void setLogTarget(File file) {
        this.logTarget = file;
    }

    public void run() {
        initialize();

        stats.startStatistics();
        assert database.getStatisticsLogger() == stats;

        simulate();

        log.info("Execution complete, showing statistics");
        outputStatistics(false, "simulator");

        writer.close();
        database.flush();
    }

    public void initialize() {
        try {
            if (logTarget != null) {
                PrintStream str = new PrintStream(new FileOutputStream(logTarget));
                writer.setTarget(str);
            }
        } catch (FileNotFoundException e) {
            log.warn("Could not find log target file " + logTarget, e);
        }
    }

    protected abstract void simulate();

    public static void main(String[] args) {
        log.info("Initializing application context");
        if (args.length < 1) {
            log.warn("Give the DB config file as parameter (exec-cmvbt.xml, for example)");
            return;
        }
        String execFile = "classpath:" + args[0];
        ApplicationContext ctx = new ClassPathXmlApplicationContext(new String[] { CFG_PATH,
            execFile });
        Simulator<?, ?> simulator = (Simulator<?, ?>) ctx.getBean(BEAN_NAME);
        log.info("Running " + simulator);
        simulator.run();
    }

}
