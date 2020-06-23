package fi.hut.cs.treelib.gui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTree;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.VisualizableTree;
import fi.hut.cs.treelib.common.AbstractTree;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.TreeShortcuts;
import fi.tuska.util.StringParser;

/**
 * GUI for showing a 2D visualization of page contents. Extremely handy for
 * pages of multiversion structures.
 * 
 * @author thaapasa
 */
public class PageMapGUI<K extends Key<K>, V extends PageValue<?>> implements KeyListener,
    MouseListener {

    private static final Logger log = Logger.getLogger(PageMapGUI.class);

    private JFrame frame;
    private PageMap<K, V> pageMap;
    private JLabel levelText;
    private int level;
    private VisualizableTree<K, V, ?> tree;
    private K keyPrototype;

    @SuppressWarnings("unchecked")
    public PageMapGUI(VisualizableTree<K, V, ?> tree, K keyPrototype, int width, int height) {
        this.tree = tree;
        this.level = 1;
        this.keyPrototype = keyPrototype;
        if (tree instanceof MVTree) {
            pageMap = new MVPageMap<K, V>((MVTree<K, V, ?>) tree, keyPrototype, width, height);
        } else if (tree instanceof MDTree) {
            pageMap = (PageMap<K, V>) new MDPageMap<K, V>((VisualizableTree<MBR<K>, V, ?>) tree,
                (MBR<K>) keyPrototype, width, height);
        } else {
            pageMap = new PageMap<K, V>(tree, keyPrototype, width, height);
        }

        frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addKeyListener(this);
        pageMap.addMouseListener(this);

        this.levelText = new JLabel(pageMap.toString());
        createComponents();
    }

    public void start() {
        frame.pack();
        frame.setVisible(true);
        repaint();
    }

    public void repaint() {
        if (frame.isVisible()) {
            pageMap.update();
        }
    }

    public void updateLabel() {
        levelText.setText(pageMap.toString());
    }

    public void setLevel(int level) {
        if (level > 0 && level <= tree.getMaxHeight()) {
            this.level = level;
            pageMap.setLevel(level);
            updateLabel();
            repaint();
        }
    }

    public void setShownVersion(Integer version) {
        if (!(pageMap instanceof MVPageMap<?, ?>)) {
            return;
        }
        MVPageMap<K, V> multiPageMap = (MVPageMap<K, V>) pageMap;
        multiPageMap.setShownVersion(version);
        updateLabel();
        repaint();
    }

    private void increaseLevel() {
        log.debug("Increasing shown tree level");
        setLevel(this.level + 1);
    }

    private void decreaseLevel() {
        log.debug("Decreasing shown tree level");
        setLevel(this.level - 1);
    }

    private void increaseVersion() {
        if (pageMap instanceof MVPageMap<?, ?>) {
            log.debug("Increasing shown version");
            MVPageMap<K, V> multiPageMap = (MVPageMap<K, V>) pageMap;
            Integer shownVersion = multiPageMap.getShownVersion();
            setShownVersion(shownVersion != null ? shownVersion + 1 : 1);
        }
    }

    private void decreaseVersion() {
        if (pageMap instanceof MVPageMap<?, ?>) {
            log.debug("Decreasing shown version");
            MVPageMap<K, V> multiPageMap = (MVPageMap<K, V>) pageMap;
            Integer shownVersion = multiPageMap.getShownVersion();
            setShownVersion(shownVersion != null ? shownVersion - 1 : 1);
        }
    }

    private void showAllVersions() {
        log.debug("Showing all versions");
        setShownVersion(null);
    }

    public void gotoVersion() {
        QueryPopup query = new QueryPopup("Goto version", frame, new QueryPopup.QueryLine(
            "Version", StringParser.INTEGER_PARSER));
        List<Object> values = query.show();
        if (values != null) {
            Integer version = (Integer) values.get(0);
            setShownVersion(version);
        }
    }

    public void saveImage() {
        log.info("Operation: Save page map image");
        GUIOperations.saveImage(frame, pageMap.getImage());
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        switch (code) {
        case KeyEvent.VK_PAGE_DOWN:
            decreaseLevel();
            break;
        case KeyEvent.VK_PAGE_UP:
            increaseLevel();
            break;
        case KeyEvent.VK_LEFT:
        case KeyEvent.VK_DOWN:
            decreaseVersion();
            break;
        case KeyEvent.VK_RIGHT:
        case KeyEvent.VK_UP:
            increaseVersion();
            break;
        default:
            break;
        }
    }

    private void createComponents() {
        Container main = frame.getContentPane();
        GridBagLayout gb = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        main.setLayout(gb);

        main.add(levelText);
        gb.addLayoutComponent(levelText, c);

        main.add(pageMap);
        gb.addLayoutComponent(pageMap, c);
    }

    protected void showPageInfo(int x, int y) {
        Page<K, V> page = pageMap.getPageByScreenCoords(x, y, GUIElements.VISUALIZER_OWNER);
        if (page != null) {
            log.info(page);
            tree.getPageBuffer().unfix(page, GUIElements.VISUALIZER_OWNER);
        }
    }

    @SuppressWarnings("unchecked")
    protected void showKeyContents(int x, int y) {
        if (pageMap instanceof MVPageMap) {
            MVPageMap<K, V> multiPageMap = (MVPageMap<K, V>) pageMap;
            int version = multiPageMap.getVersionByScreenCoords(x, y);
            MVTree<K, V, ?> multiTree = (MVTree<K, V, ?>) tree;
            log.info(String.format("Contents at version %d: %s", version, TreeShortcuts.getRange(
                multiTree, keyPrototype.getEntireRange(), new DummyTransaction<K, V>(version,
                    AbstractTree.DEFAULT_TRANSACTION_ID, GUIElements.VISUALIZER_OWNER))));
        }
    }

    protected void selectVersionByCoords(int x, int y) {
        if (pageMap instanceof MVPageMap<?, ?>) {
            MVPageMap<K, V> multiPageMap = (MVPageMap<K, V>) pageMap;
            int version = multiPageMap.getVersionByScreenCoords(x, y);
            setShownVersion(version);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // No action
        char c = Character.toLowerCase(e.getKeyChar());
        switch (c) {
        case 'q':
            // q to close page map gui window
            frame.setVisible(false);
            break;
        case 'a':
            // a to show all versions
            showAllVersions();
            break;
        case 'g':
            // g to go to a certain version
            gotoVersion();
            break;
        case 's':
            // s to save map image
            saveImage();
            break;
        default:
            break;
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() >= 2) {
            showKeyContents(e.getX(), e.getY());
        }
        if (e.getButton() == MouseEvent.BUTTON1) {
            showPageInfo(e.getX(), e.getY());
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            selectVersionByCoords(e.getX(), e.getY());
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // No action
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Nothing
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // Nothing
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // Nothing
    }

}
