package fi.hut.cs.treelib.console;

import java.awt.Container;
import java.awt.LayoutManager;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.MDTransaction;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.controller.VisualizerController;
import fi.hut.cs.treelib.gui.DummyFontMetrics;
import fi.hut.cs.treelib.gui.GUIKeyHandler;
import fi.hut.cs.treelib.gui.GUIOperations;
import fi.hut.cs.treelib.gui.TreeDrawStyle;
import fi.hut.cs.treelib.gui.TreeDrawer;
import fi.hut.cs.treelib.operations.CheckOperation;
import fi.hut.cs.treelib.operations.InspectOperation;
import fi.hut.cs.treelib.operations.MDInspectOperation;
import fi.hut.cs.treelib.stats.DefaultStatisticsPrinter;
import fi.hut.cs.treelib.stats.NoStatistics;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.StatisticsPrinter;
import fi.tuska.util.AssertionSupport;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Converter;
import fi.tuska.util.Pair;
import fi.tuska.util.media.gui.ContainerUtils;

public class TreeConsole<K extends Key<K>, V extends PageValue<?>> implements
    VisualizerController<K> {

    public static final String BEAN_NAME = "treeConsole";
    private static final String CFG_PATH = "console.xml";
    private static final Logger log = Logger.getLogger(TreeConsole.class);

    private Database<K, V, ?> database;
    private StatisticsLogger statisticsLogger = NoStatistics.instance();
    private StatisticsPrinter statisticsPrinter = new DefaultStatisticsPrinter();

    private JFrame frame;
    private PageDrawerGUI<K> pageDrawer;
    private GUIKeyHandler<K> handler;

    private static final String[] help = new String[] { "Usage instructions", "h, ?: show help",
        "p: draw tree pages", ".: show database info", "%: run check operation",
        "!: run inspect utility", ":: show page info", "q: quit" };

    public void setDatabase(Database<K, V, ?> database) {
        this.database = database;
    }

    public void setStatisticsLogger(StatisticsLogger statisticsLogger) {
        this.statisticsLogger = statisticsLogger;
    }

    public void run() {
        initComponents();
        database.setStatisticsLogger(statisticsLogger);
    }

    private void initComponents() {
        frame = new JFrame("Tree console");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container main = frame.getContentPane();
        LayoutManager layout = new BoxLayout(main, BoxLayout.Y_AXIS);
        main.setLayout(layout);
        main.add(ContainerUtils.getPaddedComponent(new JLabel(
            "Enter command by pressing command key;"), 15, 4));
        main.add(ContainerUtils.getPaddedComponent(new JLabel("type 'h' for help."), 15, 4));

        handler = new GUIKeyHandler<K>(this, database.getKeyPrototype(), statisticsLogger, frame) {
            @Override
            protected String[] getHelp() {
                return help;
            }

            @Override
            protected boolean requireActiveTransaction(String operationName) {
                return true;
            }

            @Override
            protected void quit() {
                log.info("Shutting down Tree Console");
                database.flush();
                System.exit(0);
            }

            @Override
            protected void showDebugInfo() {
                showDebug();
            }

            @Override
            protected void customKeyTyped(KeyEvent e) {
                final char c = Character.toLowerCase(e.getKeyChar());
                switch (c) {
                case 'p':
                    if (database instanceof MDDatabase<?, ?, ?>) {
                        showPageDrawer();
                    }
                    break;

                case '!':
                    runInspect();
                    break;

                case '%':
                    runCheck();
                    break;

                case 's':
                    showStatistics();
                    break;

                case '3':
                    saveTikZModel();
                    break;

                case '4':
                    saveSketchModel();
                    break;

                default:
                    break;
                }

            }

            @Override
            protected void repaint() {
            }

            @Override
            protected void runMaintenance() {
                database.requestMaintenance();
            }

            @Override
            protected void checkDBConsistency() {
                database.checkConsistency(true);
            }
        };
        frame.addKeyListener(handler);

        frame.setVisible(true);
        frame.pack();
    }

    public void saveTikZModel() {
        log.info("Operation: Save TikZ model");
        GUIOperations.saveTikZModel(frame, new TreeDrawer<K, V>(database, database
            .getDatabaseTree(), database.getCommittedVersion(), true, TreeDrawStyle.EPS,
            new DummyFontMetrics()));
    }

    public void saveSketchModel() {
        log.info("Operation: Save Sketch model");
        GUIOperations.saveSketchModel(frame, new TreeDrawer<K, V>(database, database
            .getDatabaseTree(), database.getCommittedVersion(), true, TreeDrawStyle.EPS,
            new DummyFontMetrics()));
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: TreeConsole [configfile]");
            return;
        }
        String config = args[0];
        log.info("Initializing application context");
        ApplicationContext ctx = new ClassPathXmlApplicationContext(new String[] { config,
            CFG_PATH });
        TreeConsole<?, ?> console = (TreeConsole<?, ?>) ctx.getBean(BEAN_NAME);
        console.run();
    }

    public void quit() {
        log.info("Shutting down Tree Console");
        database.flush();
        System.exit(0);
    }

    private void showPageDrawer() {
        log.info("Showing tree page drawer");
        if (pageDrawer == null) {
            pageDrawer = new PageDrawerGUI<K>(database);
        }
        pageDrawer.show();
    }

    private void showDebug() {
        log.info("Debug information for Tree Console");
        database.printDebugInfo();
    }

    private void showStatistics() {
        StatisticsLogger stats = database.getStatisticsLogger();
        if (!stats.isStarted()) {
            log.info("Starting statistics");
            stats.startStatistics();
        } else {
            log.info("Dumping and resetting statistics");
            statisticsPrinter.showStatistics(stats, database, "console");
            stats.clear();
            stats.startStatistics();
        }
    }

    @SuppressWarnings("unchecked")
    private void runInspect() {
        log.info("Running inspect");
        Logger logger = Logger.getLogger("fi.hut.cs.treelib");
        Level origLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        if (database instanceof MDDatabase) {
            new MDInspectOperation<K, V>((MDDatabase<K, V, ?>) database).execute(null);
        } else {
            new InspectOperation<K, V>(database).execute(null);
        }
        logger.setLevel(origLevel);
        log.info("Inspection complete");
    }

    private void runCheck() {
        log.info("Running check, assertions: " + AssertionSupport.isAssertionsEnabled());
        Logger logger = Logger.getLogger("fi.hut.cs.treelib");
        Level origLevel = logger.getLevel();

        log.info("Running database check operation");
        logger.setLevel(Level.WARN);
        new CheckOperation<K, V>(database, statisticsLogger).execute(null);

        logger.setLevel(origLevel);
        log.info("Inspection complete");
    }

    @Override
    public void begin() {
        log.debug("begin() not applicable");
    }

    @Override
    public void commit() {
        log.debug("commit() not applicable");
    }

    @Override
    public boolean delete(K key) {
        Transaction<K, V> tx = database.beginTransaction();
        boolean res = tx.delete(key);
        log.debug(String.format("Deleted key %s, success: %s", key, res ? "ok" : "fail"));
        tx.commit();
        return res;
    }

    @Override
    public int getLatestVersion() {
        return database.getLatestVersion();
    }

    @Override
    public List<String> getOperationLog() {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean insert(K key, String value) {
        V val = (V) database.getValuePrototype().parse(value);
        Transaction<K, V> tx = database.beginTransaction();
        boolean res = tx.insert(key, val);
        log.debug(String.format("Inserting key %s, value %s (%s), result: %s", key, val, value,
            res ? "ok" : "fail"));
        if (res)
            tx.commit();
        else
            tx.abort();
        return res;
    }

    @Override
    public boolean isActiveTransaction() {
        return false;
    }

    @Override
    public boolean isMultiVersion() {
        return database.isMultiVersion();
    }

    @Override
    public String query(K key) {
        Transaction<K, V> tx = database.beginTransaction();
        V res = tx.get(key);
        tx.commit();
        return String.valueOf(res);
    }

    @Override
    public String query(K key, int version) {
        if (!isMultiVersion())
            throw new UnsupportedOperationException();

        Transaction<K, V> tx = database.beginReadTransaction(version);
        V res = tx.get(key);
        tx.commit();
        return String.valueOf(res);
    }

    private final Converter<Pair<K, V>, Pair<K, String>> RESULT_LIST_CONVERTER = new Converter<Pair<K, V>, Pair<K, String>>() {
        @Override
        public Pair<K, String> convert(Pair<K, V> source) {
            return new Pair<K, String>(source.getFirst(), source.getSecond().getValue()
                .toString());
        }
    };

    @Override
    public List<Pair<K, String>> rangeQuery(KeyRange<K> range) {
        Transaction<K, V> t = database.beginTransaction();
        List<Pair<K, V>> res = t.getRange(range);
        t.commit();
        return res != null ? CollectionUtils.convertList(res, RESULT_LIST_CONVERTER) : null;
    }

    @Override
    public List<Pair<K, String>> rangeQuery(KeyRange<K> range, int version) {
        Transaction<K, V> tx = database.beginReadTransaction(version);
        List<Pair<K, V>> res = tx.getRange(range);
        tx.commit();
        return res != null ? CollectionUtils.convertList(res, RESULT_LIST_CONVERTER) : null;
    }

    @Override
    public boolean runAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean signalAdvance() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Database<K, ?, ?> getDatabase() {
        return database;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> overlapQuery(K key) {
        if (!database.isMultiDimension())
            throw new UnsupportedOperationException();
        MDDatabase<K, V, ?> mdd = (MDDatabase<K, V, ?>) database;
        MDTransaction<K, V> t = mdd.beginTransaction();
        MBR<K> mbrK = (MBR<K>) key;
        List<Pair<MBR<K>, V>> valList = t.getOverlapping(mbrK);
        List<String> result = new ArrayList<String>(valList.size());
        for (Pair<MBR<K>, V> v : valList) {
            result.add(String.valueOf(v.getSecond()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> exactQuery(K key) {
        if (!database.isMultiDimension())
            throw new UnsupportedOperationException();
        MDDatabase<K, V, ?> mdd = (MDDatabase<K, V, ?>) database;
        MDTransaction<K, V> t = mdd.beginTransaction();
        MBR<K> mbrK = (MBR<K>) key;
        List<V> valList = t.getExact(mbrK);
        List<String> result = new ArrayList<String>(valList.size());
        for (V v : valList) {
            result.add(String.valueOf(v));
        }
        return result;
    }

    @Override
    public void flush() {
        database.flush();
    }
}
