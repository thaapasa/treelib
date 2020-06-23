package fi.hut.cs.treelib.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;

import javax.swing.JComponent;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Tree;

/**
 * A canvas on which the tree is drawn.
 * 
 * @author thaapasa
 */
public class TreeDrawCanvas<K extends Key<K>, V extends PageValue<?>> extends JComponent {

    private static final Logger log = Logger.getLogger(TreeDrawCanvas.class);

    private static final Color BG_COLOR = new Color(255, 255, 255);
    private static final long serialVersionUID = -7748425601912734441L;

    private BufferedImage buffer;

    private Tree<K, V, ?> tree;
    private TreeDrawer<K, V> treeDrawer;
    private boolean showAllVersions;
    private int version;

    protected static final BufferedImage DUMMY_IMAGE = new BufferedImage(1, 1,
        BufferedImage.TYPE_INT_RGB);

    public TreeDrawCanvas(Database<K, V, ?> database, Tree<K, V, ?> tree, int version) {
        this.tree = tree;
        this.treeDrawer = new TreeDrawer<K, V>(database, tree, version, false,
            TreeDrawStyle.SCREEN_NO_TX, DUMMY_IMAGE.getGraphics().getFontMetrics());
        this.version = 0;

        updateTree();
    }

    public void setIsActiveTransaction(boolean isActive) {
        if (isActive) {
            treeDrawer.setTreeDrawStyle(TreeDrawStyle.SCREEN_ACTIVE_TX);
        } else {
            treeDrawer.setTreeDrawStyle(TreeDrawStyle.SCREEN_NO_TX);
        }
    }

    public void setTree(Tree<K, V, ?> tree, int version) {
        this.tree = tree;
        this.version = version;
        this.treeDrawer.setTree(tree, version, showAllVersions);
    }

    public void setShowAllVersions(boolean show) {
        this.showAllVersions = show;
        setTree(tree, version);
    }

    public TreeDrawer<K, V> getTreeDrawer() {
        return treeDrawer;
    }

    public Tree<K, V, ?> getTree() {
        return tree;
    }

    public RenderedImage getRenderedImage() {
        return buffer;
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(BG_COLOR);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (buffer != null) {
            g.drawImage(buffer, 0, 0, buffer.getWidth(), buffer.getHeight(), this);
        }
    }

    public void updateTree() {
        // Update size
        treeDrawer.update();
        Dimension size = treeDrawer.getSize();

        log.debug("Recreating buffer image");
        buffer = new BufferedImage((int) size.getWidth(), (int) size.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        Graphics g = buffer.getGraphics();
        treeDrawer.drawTree(tree, g);

        setSize(size);
        setMinimumSize(size);
        setPreferredSize(size);
        setMaximumSize(size);

        repaint();
    }

}
