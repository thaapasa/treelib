package fi.hut.cs.treelib.gui;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.RenderedImage;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.VisualizableTree;
import fi.hut.cs.treelib.controller.VisualizerController;
import fi.hut.cs.treelib.stats.DefaultStatisticsPrinter;
import fi.hut.cs.treelib.stats.NoStatistics;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.StatisticsPrinter;

public class TreeVisualizerGUI<K extends Key<K>, V extends PageValue<?>> implements Component {

    private static final Logger log = Logger.getLogger(TreeVisualizerGUI.class);
    private static final Dimension START_SIZE = new Dimension(1000, 710);

    private final JFrame frame;
    private final TreeDrawCanvas<K, V> treeCanvas;
    private final Database<K, V, ? extends Page<K, V>> database;
    private final GUIController<K, V> guiController;
    private PageMapGUI<K, V> pageMapGUI;

    private static final int PAGE_MAP_WIDTH = 1200;
    private static final int PAGE_MAP_HEIGHT = 700;

    private final K keyPrototype;
    private int shownVersion;
    private boolean showAllVersions = false;

    private StatisticsLogger statisticsLogger = NoStatistics.instance();
    private StatisticsPrinter statisticsPrinter = new DefaultStatisticsPrinter();

    public TreeVisualizerGUI(Database<K, V, ?> database, K keyPrototype,
        VisualizerController<K> controller, StatisticsLogger statisticsLogger) {
        this.database = database;
        this.keyPrototype = keyPrototype;
        this.frame = new JFrame("Tree Visualizer");
        this.shownVersion = 0;
        this.statisticsLogger = statisticsLogger;
        this.treeCanvas = new TreeDrawCanvas<K, V>(database, database.getDatabaseTree(),
            shownVersion);
        this.guiController = new GUIController<K, V>(this, controller, keyPrototype,
            statisticsLogger);

        initializeComponents();
        toggleShowAllVersions();
    }

    public TreeDrawCanvas<K, V> getTreeDrawCanvas() {
        return treeCanvas;
    }

    public RenderedImage getRenderedImage() {
        return treeCanvas.getRenderedImage();
    }

    public JFrame getFrame() {
        return frame;
    }

    public Database<K, V, ? extends Page<K, V>> getDatabase() {
        return database;
    }

    public void start() {
        log.info("Starting visualizer GUI");
        latestVersion();
        frame.pack();
        frame.setSize(START_SIZE);
        frame.setVisible(true);
    }

    private void printDebugInfo(Object o) {
        try {
            Component comp = (Component) o;
            comp.printDebugInfo();
        } catch (ClassCastException e) {
            log.debug(String.format("Not debuggable class %s: %s", o.getClass(), o.toString()));
        }
    }

    @Override
    public void printDebugInfo() {
        log.debug("Database:");
        printDebugInfo(database);
        log.debug("Page buffer:");
        printDebugInfo(database.getPageBuffer());
    }

    protected void showStatistics() {
        statisticsPrinter.showStatistics(statisticsLogger, database, "visualizer");
    }

    private void initializeComponents() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Container main = new Container();
        GridBagLayout layout = new GridBagLayout();
        main.setLayout(layout);
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;

        main.add(treeCanvas);
        layout.addLayoutComponent(treeCanvas, c);

        JScrollPane pane = new JScrollPane(main,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        frame.getContentPane().add(pane);

        frame.addKeyListener(guiController);
    }

    public void repaint() {
        treeCanvas.setIsActiveTransaction(guiController.isActiveTransaction());
        treeCanvas.updateTree();
        frame.getContentPane().validate();
        if (pageMapGUI != null) {
            pageMapGUI.repaint();
        }
    }

    public void latestVersion() {
        if (database.isMultiVersion()) {
            showVersion(database.getMVDatabase().getDatabaseTree().getLatestVersion());
        } else {
            repaint();
        }
    }

    public void toggleShowAllVersions() {
        if (database.isMultiVersion()) {
            showAllVersions = !showAllVersions;
            log.info("Toggling all versions showing " + (showAllVersions ? "on" : "off"));
            treeCanvas.setShowAllVersions(showAllVersions);
            repaint();
        }
    }

    public void showPageMap() {
        log.info("Showing page content map");
        if (pageMapGUI == null) {
            this.pageMapGUI = new PageMapGUI<K, V>((VisualizableTree<K, V, ?>) database
                .getDatabaseTree(), keyPrototype, PAGE_MAP_WIDTH, PAGE_MAP_HEIGHT);
        }
        pageMapGUI.start();
    }

    public void showVersion(int version) {
        if (database.isMultiVersion()) {
            if (version < 0) {
                version = 0;
            }
            MVTree<K, V, ?> mvTree = database.getMVDatabase().getDatabaseTree();
            if (version > mvTree.getLatestVersion()) {
                version = mvTree.getLatestVersion();
            }
            shownVersion = version;
            treeCanvas.setTree(mvTree.getVersionTree(version), shownVersion);
            repaint();
        } else {
            throw new AssertionError("showVersion() called for non-MV-tree "
                + database.getDatabaseTree());
        }
    }

    public void subtractShownVersion() {
        if (database.isMultiVersion()) {
            showVersion(Math.max(shownVersion - 1, database.getMVDatabase().getDatabaseTree()
                .getFirstVersion()));
        }
    }

    public void increaseShownVersion() {
        if (database.isMultiVersion()) {
            int v = Math.min(shownVersion + 1, database.getMVDatabase().getDatabaseTree()
                .getLatestVersion());
            if (v == shownVersion) {
                guiController.advanceController();
            } else {
                showVersion(v);
            }
        }
    }

    public void runDBMaintenance() {
        database.requestMaintenance();
        repaint();
    }

    public void quit() {
        log.info("Shutting down Tree Visualizer");
        database.flush();
        System.exit(0);
    }

    @Override
    public void checkConsistency(Object... params) {
        log.info("Checking database consistency");
        database.checkConsistency(params);
    }

}
