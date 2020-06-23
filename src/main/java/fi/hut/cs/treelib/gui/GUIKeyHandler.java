package fi.hut.cs.treelib.gui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.controller.VisualizerController;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.DefaultStatisticsPrinter;
import fi.hut.cs.treelib.stats.StatisticsPrinter;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.StoredPage;
import fi.hut.cs.treelib.util.GUIUtils;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Pair;
import fi.tuska.util.StringParser;

public abstract class GUIKeyHandler<K extends Key<K>> implements KeyListener {

    private static final Logger log = Logger.getLogger(GUIKeyHandler.class);

    protected K keyPrototype;
    protected StatisticsLogger statisticsLogger;
    protected VisualizerController<K> controller;
    protected JFrame parent;
    private StatisticsPrinter statisticsPrinter = new DefaultStatisticsPrinter();

    public GUIKeyHandler(VisualizerController<K> controller, K keyPrototype,
        StatisticsLogger statisticsLogger, JFrame parent) {
        this.controller = controller;
        this.statisticsLogger = statisticsLogger;
        this.keyPrototype = keyPrototype;
        this.parent = parent;
    }

    @Override
    public final void keyTyped(KeyEvent e) {
        final char c = Character.toLowerCase(e.getKeyChar());
        switch (c) {

        case 'i':
            // i to insert an entry into the tree
            insertEntry();
            break;
        case 'd':
            // d to delete an entry from the tree
            deleteEntry();
            break;
        case 'q':
            // q to quit
            quit();
            break;
        case 'g':
            // g to query (get) a value
            queryEntry();
            break;
        case 'r':
            // r to query for a range of values
            queryRange();
        case 'b':
            // b to begin transaction
            beginTransaction();
            break;
        case 'c':
            // c to commit transaction
            commitTransaction();
            break;
        case '+':
            // + to perform an overlap query (MD DB)
            overlapQuery();
            break;
        case '*':
            // * to perform an exact query (MD DB)
            exactQuery();
            break;
        case '&':
            // & to perform consistency check
            log.info("Operation: Check consistency");
            checkDBConsistency();
            break;
        case 'h':
        case '?':
            // h or ? to show help page
            showHelp();
            break;
        case 't':
            // t to start statistics/show statistics information
            doStatistics();
            break;
        case '.':
            showDebugInfo();
            break;
        case ':':
            showPageDebug();
            break;
        case 'm':
            runMaintenance();
            break;
        case 'f':
            flush();
            break;
        default:
            customKeyTyped(e);
            break;
        }
    }

    protected abstract void customKeyTyped(KeyEvent e);

    protected abstract void runMaintenance();

    protected abstract void checkDBConsistency();

    @Override
    public void keyPressed(KeyEvent e) {
        // Nothing
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Nothing
    }

    protected abstract void quit();

    protected abstract void showDebugInfo();

    protected abstract String[] getHelp();

    protected void doStatistics() {
        if (statisticsLogger.isStarted()) {
            statisticsPrinter.showStatistics(statisticsLogger, controller.getDatabase(),
                "manual-request");
        } else {
            statisticsLogger.startStatistics();
            boolean success = statisticsLogger.isStarted();
            log.debug(String.format("Starting %s... %s", statisticsLogger, success ? "success"
                : "failure"));
        }
    }

    public void beginTransaction() {
        log.info("Operation: Begin transaction");
        controller.begin();
        repaint();
    }

    public void commitTransaction() {
        log.info("Operation: Commit transaction");
        controller.commit();
        repaint();
    }

    public void flush() {
        log.info("Operation: Flush");
        controller.flush();
    }

    protected abstract void repaint();

    public void showHelp() {
        log.info("Operation: Show help");
        String[] help = getHelp();
        for (String helpLine : help) {
            log.info(helpLine);
        }
        GUIUtils.showTextDialog("Help", help, parent);
    }

    protected boolean requireActiveTransaction(String operationName) {
        if (controller.isActiveTransaction())
            return true;
        String msg = "No active transaction, cannot perform operation: " + operationName;
        GUIUtils.showTextDialog("Error", new String[] { msg }, parent);
        log.warn(msg);
        return false;
    }

    @SuppressWarnings("unchecked")
    protected boolean insertEntry() {
        log.info("Operation: Insert entry");
        if (!requireActiveTransaction("insert"))
            return false;

        QueryPopup query = new QueryPopup("Insert key to tree", parent, new QueryPopup.QueryLine(
            "Key", keyPrototype), new QueryPopup.QueryLine("Value", StringParser.STRING_PARSER));

        List<Object> values = query.show();
        if (values == null)
            return false;

        K key = (K) values.get(0);
        String value = (String) values.get(1);
        // Auto-fill key as value if no value entered
        if ("".equals(value)) {
            value = key.toString();
        }
        boolean res = controller.insert(key, value);
        return res;
    }

    @SuppressWarnings("unchecked")
    protected boolean deleteEntry() {
        log.info("Operation: Delete entry");
        if (!requireActiveTransaction("delete"))
            return false;
        QueryPopup query = new QueryPopup("Delete key from tree", parent,
            new QueryPopup.QueryLine("Key", keyPrototype));

        List<Object> values = query.show();
        if (values == null)
            return false;

        K key = (K) values.get(0);
        boolean res = controller.delete(key);
        log.info(res ? "Delete successful" : "No object deleted");
        return res;
    }

    @SuppressWarnings("unchecked")
    protected void queryEntry() {
        log.info("Operation: Query (get) entry");
        if (!requireActiveTransaction("query"))
            return;

        QueryPopup query = controller.isMultiVersion() ? new QueryPopup("Query entry from tree",
            parent, new QueryPopup.QueryLine("Key", keyPrototype), new QueryPopup.QueryLine(
                "Version", controller.getLatestVersion(), StringParser.INTEGER_PARSER))
            : new QueryPopup("Query entry from tree", parent, new QueryPopup.QueryLine("Key",
                keyPrototype));

        List<Object> values = query.show();
        if (values != null) {
            K key = (K) values.get(0);
            if (log.isInfoEnabled())
                log.info("Query key: " + key.toString());

            String value = null;
            if (controller.isMultiVersion()) {
                Integer version = (Integer) values.get(1);
                if (version == null) {
                    version = controller.getLatestVersion();
                }
                value = controller.query(key, version);
            } else {
                value = controller.query(key);
            }

            if (value != null) {
                if (log.isInfoEnabled())
                    log.info("Found value: " + value + " for key " + key);
                JOptionPane.showMessageDialog(parent, "Value: " + value, "Value found",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                if (log.isInfoEnabled())
                    log.info("Key " + key + " not found from database");
                JOptionPane.showMessageDialog(parent, "No value found with given key",
                    "Value not found", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void queryRange() {
        log.info("Operation: Query (get) range of entry");
        if (!requireActiveTransaction("range-query"))
            return;

        QueryPopup.QueryLine minKeyQ = new QueryPopup.QueryLine("Min-key", keyPrototype);
        QueryPopup.QueryLine maxKeyQ = new QueryPopup.QueryLine("Max-key", keyPrototype);
        QueryPopup.QueryLine versionQ = new QueryPopup.QueryLine("Version", controller
            .getLatestVersion(), StringParser.INTEGER_PARSER);

        QueryPopup query = controller.isMultiVersion() ? new QueryPopup("Query entry from tree",
            parent, minKeyQ, maxKeyQ, versionQ) : new QueryPopup("Query entry from tree", parent,
            minKeyQ, maxKeyQ);

        List<Object> values = query.show();
        if (values != null) {
            K minKey = (K) values.get(0);
            K maxKey = (K) values.get(1);
            log.info("Query keys: " + minKey.toString() + " - " + maxKey.toString());

            KeyRange<K> range = new KeyRangeImpl<K>(minKey, maxKey);
            List<Pair<K, String>> value = null;
            if (controller.isMultiVersion()) {
                Integer version = (Integer) values.get(2);
                if (version == null) {
                    version = controller.getLatestVersion();
                }
                value = controller.rangeQuery(range, version);
            } else {
                value = controller.rangeQuery(range);
            }

            if (value != null) {
                JOptionPane.showMessageDialog(parent, "Value: " + value, "Value found",
                    JOptionPane.INFORMATION_MESSAGE);
                for (Pair<K, String> v : value) {
                    System.out.println(v.getFirst() + ": " + v.getSecond());
                }
            } else {
                JOptionPane.showMessageDialog(parent, "No value found with given key",
                    "Value not found", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void overlapQuery() {
        log.info("Operation: Overlap query (get) entries");
        if (!controller.getDatabase().isMultiDimension())
            return;
        if (!requireActiveTransaction("overlap query"))
            return;

        QueryPopup query = new QueryPopup("Overlap query", parent, new QueryPopup.QueryLine(
            "Key", keyPrototype));

        List<Object> inputs = query.show();
        if (inputs == null)
            return;

        K key = (K) inputs.get(0);
        List<String> list = controller.overlapQuery(key);

        if (list != null && !list.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Value: " + CollectionUtils.join(list, ", "),
                "Value found", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(parent, "No values found with given key",
                "Value not found", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    protected void exactQuery() {
        log.info("Operation: Exact query (get) entries");
        if (!controller.getDatabase().isMultiDimension())
            return;
        if (!requireActiveTransaction("exact query"))
            return;

        QueryPopup query = new QueryPopup("Exact query", parent, new QueryPopup.QueryLine("Key",
            keyPrototype));

        List<Object> inputs = query.show();
        if (inputs == null)
            return;

        K key = (K) inputs.get(0);
        List<String> list = controller.exactQuery(key);

        if (list != null && !list.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Value: " + CollectionUtils.join(list, ", "),
                "Value found", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(parent, "No values found with given key",
                "Value not found", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    protected void showPageDebug() {
        log.info("Operation: Show page debug information");
        PageBuffer buffer = controller.getDatabase().getPageBuffer();
        StoredPage page = GUIElements.selectPage(parent, "Select page to debug", buffer,
            GUIElements.VISUALIZER_OWNER);
        if (page != null) {
            if (page instanceof Component) {
                ((Component) page).printDebugInfo();
            } else {
                log.info("Selected page is not a Component");
            }
            buffer.unfix(page, GUIElements.VISUALIZER_OWNER);
        }

    }

}
