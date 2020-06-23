package fi.hut.cs.treelib.gui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.controller.VisualizerController;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.util.GUIUtils;
import fi.tuska.util.StringParser;
import fi.tuska.util.file.FileUtils;

/**
 * GUI controller class. Determines the actions to do based on user input.
 * 
 * @author thaapasa
 * 
 * @param <K>
 */

public class GUIController<K extends Key<K>, V extends PageValue<?>> extends GUIKeyHandler<K>
    implements KeyListener {

    private static final Logger log = Logger.getLogger(GUIController.class);

    private static final String[] help = new String[] { "Usage instructions",
        "space: advance tree creator", "left/down: show previous version of MV tree",
        "right/up: show next version of MV tree", "v: goto version", "b: begin transaction",
        "c: commit transaction", "g: query (get) value from tree",
        "i: insert an entry into the tree", "d: delete an entry from the tree",
        "s: save current tree as PNG image", "l: save operation log to a log file",
        "h: show this help", "q: quit", "p: show page content visualization",
        "a: toggle show all versions on/off", "e: save an EPS image of the current tree",
        ".: show debug info", ":: show page debug info", "+: overlap query (MD-DBs)",
        "*: exact query (MD-DBs)" };

    private TreeVisualizerGUI<K, V> gui;

    public GUIController(TreeVisualizerGUI<K, V> gui, VisualizerController<K> controller,
        K keyPrototype, StatisticsLogger statisticsLogger) {
        super(controller, keyPrototype, statisticsLogger, gui.getFrame());
        this.gui = gui;
    }

    @Override
    public void customKeyTyped(KeyEvent e) {
        final char c = Character.toLowerCase(e.getKeyChar());
        switch (c) {
        case ' ':
            advanceController();
            break;
        case 's':
            // s to save the image in a file
            saveImage();
            break;

        case 'v':
            // g to goto version
            gotoVersion();
            break;
        case 'l':
            // l to save operation log
            saveLog();
            break;
        case 'p':
            // p to show page content map
            gui.showPageMap();
            break;
        case 'a':
            // a to show all versions of the tree
            gui.toggleShowAllVersions();
            break;
        case 'e':
            saveEPS();
            break;
        case 'k':
            saveTikZ();
            break;
        case '3':
            saveTikZModel();
            break;
        case '4':
            saveSketchModel();
            break;
        case '!':
            checkConsistency();
            break;
        default:
            break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_DOWN) {
            gui.subtractShownVersion();
        } else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_UP) {
            gui.increaseShownVersion();
        }
    }

    public void saveLog() {
        log.info("Operation: Save operation log");
        File file = GUIUtils.chooseLogFile(gui.getFrame());
        if (file != null) {
            log.info(String.format("Saving operation log to file %s", file.getAbsolutePath()));
            FileUtils.writeToFile(file, controller.getOperationLog());
        }
    }

    public boolean isActiveTransaction() {
        return controller.isActiveTransaction();
    }

    public void saveImage() {
        log.info("Operation: Save image");
        GUIOperations.saveImage(gui.getFrame(), gui.getRenderedImage());
    }

    public void saveEPS() {
        log.info("Operation: Save EPS");
        GUIOperations.saveEPS(gui.getFrame(), gui.getTreeDrawCanvas().getTreeDrawer()
            .createTreeDrawer(TreeDrawStyle.EPS));
    }

    public void saveTikZ() {
        log.info("Operation: Save TikZ");
        GUIOperations.saveTikZ(gui.getFrame(), gui.getTreeDrawCanvas().getTreeDrawer()
            .createTreeDrawer(TreeDrawStyle.EPS));
    }

    public void saveTikZModel() {
        log.info("Operation: Save tree model as TikZ");
        GUIOperations.saveTikZModel(gui.getFrame(), gui.getTreeDrawCanvas().getTreeDrawer()
            .createTreeDrawer(TreeDrawStyle.EPS));
    }

    public void saveSketchModel() {
        log.info("Operation: Save Sketch model");
        GUIOperations.saveSketchModel(gui.getFrame(), gui.getTreeDrawCanvas().getTreeDrawer()
            .createTreeDrawer(TreeDrawStyle.EPS));
    }

    @Override
    public void showDebugInfo() {
        log.info("Operation: Show debug information");
        gui.printDebugInfo();
    }

    public void checkConsistency() {
        log.info("Operation: Check consistency");
        gui.checkConsistency();
    }

    public void gotoVersion() {
        log.info("Operation: Goto version");
        QueryPopup query = new QueryPopup("Goto version", gui.getFrame(),
            new QueryPopup.QueryLine("Version", StringParser.INTEGER_PARSER));
        List<Object> values = query.show();
        if (values != null) {
            Integer version = (Integer) values.get(0);
            gui.showVersion(version);
        }
    }

    @Override
    protected boolean insertEntry() {
        if (!super.insertEntry())
            return false;

        gui.latestVersion();
        return true;
    }

    @Override
    protected boolean deleteEntry() {
        if (!super.deleteEntry())
            return false;

        gui.latestVersion();
        return true;
    }

    public void advanceController() {
        log.info("Operation: Advance controller");
        // Space to forward
        if (controller != null) {
            controller.signalAdvance();
            log.debug("Operation executed, signaling GUI");
            gui.latestVersion();
        }
    }

    @Override
    public void quit() {
        gui.quit();
    }

    @Override
    protected String[] getHelp() {
        return help;
    }

    @Override
    protected void repaint() {
        gui.repaint();
    }

    @Override
    protected void runMaintenance() {
        gui.runDBMaintenance();
    }

    @Override
    protected void checkDBConsistency() {
        gui.checkConsistency(true);
    }

}
