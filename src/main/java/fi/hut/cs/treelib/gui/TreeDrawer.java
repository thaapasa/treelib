package fi.hut.cs.treelib.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.LinkPage;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.gui.PagePositionMap.PositionInfo;
import fi.hut.cs.treelib.gui.VisualPage.TextLine;

/**
 * Draws a database tree.
 * 
 * @author thaapasa
 */
public class TreeDrawer<K extends Key<K>, V extends PageValue<?>> {

    private static final Logger log = Logger.getLogger(TreeDrawer.class);

    private static final Dimension DEFAULT_PAGE_SIZE = new Dimension(30, 30);

    private static final int VERTICAL_SPACING = 40;
    private static final int HORIZONTAL_SPACING = 13;

    private static final int TITLE_HEIGHT = 40;
    private static final int PADDING = 5;

    private static final int DRAW_X_OFFSET = PADDING;
    private static final int INITIAL_Y_OFFSET = PADDING;

    private static final int MIN_WIDTH = 300;
    private static final int MIN_HEIGHT = 50;

    private int version;

    private Tree<K, V, ?> tree;
    private PagePositionMap posMap;
    private Dimension canvasSize;
    private boolean showAllVersions;
    private Database<K, V, ?> database;
    private TreeDrawStyle scheme;
    private FontMetrics metrics;
    private int yOffset = INITIAL_Y_OFFSET;

    public TreeDrawer(Database<K, V, ?> database, Tree<K, V, ?> tree, int version,
        boolean showAllVersions, TreeDrawStyle scheme, FontMetrics metrics) {
        this.database = database;
        this.scheme = scheme;
        this.metrics = metrics;
        assert metrics != null;
        setTree(tree, version, showAllVersions);
    }

    public TreeDrawStyle getScheme() {
        return scheme;
    }

    public int getVersion() {
        return version;
    }

    public void setTreeDrawStyle(TreeDrawStyle scheme) {
        this.scheme = scheme;
    }

    public TreeDrawer<K, V> createTreeDrawer(TreeDrawStyle newScheme) {
        TreeDrawer<K, V> res = new TreeDrawer<K, V>(database, tree, version, showAllVersions,
            newScheme, metrics);
        return res;
    }

    public void setTree(Tree<K, V, ?> tree, int version, boolean showAllVersions) {
        this.tree = tree;
        this.version = version;
        this.showAllVersions = showAllVersions;
        update();
    }

    public Dimension getSize() {
        return canvasSize;
    }

    private int getMaxHeight() {
        if (showAllVersions) {
            int maxHeight = 0;
            for (int showVersion : database.getSeparateRootedVersions()) {
                Tree<K, V, ?> drawTree = database.getMVDatabase().getDatabaseTree()
                    .getVersionTree(showVersion);
                maxHeight = Math.max(maxHeight, drawTree.getHeight());
            }
            return maxHeight;
        } else {
            return tree.getHeight();
        }
    }

    public void setFontMetrics(FontMetrics metrics) {
        this.metrics = metrics;
    }

    public void update() {
        assert metrics != null;
        int maxHeight = getMaxHeight();
        assert maxHeight >= 0;

        // Create new position map
        this.posMap = new PagePositionMap(maxHeight, version, HORIZONTAL_SPACING,
            VERTICAL_SPACING, scheme.getFontHeight(), DEFAULT_PAGE_SIZE, scheme, metrics);

        boolean treeExists = false;
        if (showAllVersions) {
            for (int showVersion : database.getSeparateRootedVersions()) {
                log.info("Updating tree of version " + showVersion);
                Tree<K, V, ?> drawTree = database.getMVDatabase().getDatabaseTree()
                    .getVersionTree(showVersion);
                treeExists |= posMap.addTree(drawTree);
            }
        } else {
            // Add the tree to position map
            treeExists = posMap.addTree(tree);
        }
        if (!treeExists) {
            canvasSize = new Dimension(MIN_WIDTH, MIN_HEIGHT);
            return;
        }
        // Center the pages
        posMap.centerPages();

        // Get the whole drawing size
        canvasSize = posMap.getSize();

        canvasSize.setSize(canvasSize.getWidth() + 2 * PADDING, canvasSize.getHeight() + 2
            * PADDING + (scheme.isShowTexts() ? TITLE_HEIGHT : 0));

        yOffset = INITIAL_Y_OFFSET;
        if (scheme.isShowTexts()) {
            yOffset += TITLE_HEIGHT;
        }
    }

    public void drawTree(TreeDrawSurface<K, V> g) {
        drawTree(tree, g);
    }

    public void drawTree(Graphics g) {
        drawTree(tree, new TreeDrawGraphics<K, V>(g, scheme, version));
    }

    public void drawTree(Tree<K, V, ?> drawTree, Graphics g) {
        drawTree(drawTree, new TreeDrawGraphics<K, V>(g, scheme, version));
    }

    public void drawTree(Tree<K, V, ?> drawTree, TreeDrawSurface<K, V> g) {
        log.debug("Tree width: " + canvasSize.getWidth());
        metrics = g.getFontMetrics();
        g.clearTree(canvasSize);

        // Draw tree name
        if (scheme.isShowTexts()) {
            g.drawTreeTitle(drawTree.toString());
        }

        if (showAllVersions) {
            for (int showVersion : database.getSeparateRootedVersions()) {
                log.info("Drawing tree of version " + showVersion);
                drawTree = database.getMVDatabase().getDatabaseTree().getVersionTree(showVersion);
                drawSingleTree(drawTree, g);
            }
        } else {
            drawSingleTree(drawTree, g);
        }
    }

    private void drawSingleTree(Tree<K, V, ?> drawTree, TreeDrawSurface<K, V> g) {
        // Draw tree
        Page<K, V> root = drawTree.getRoot(GUIElements.VISUALIZER_OWNER);
        if (root == null)
            return;

        VisualizablePage<K, V> vRoot = (VisualizablePage<K, V>) root;

        drawTreeLevel(vRoot, g, drawTree.getName());
        if (drawTree.getPageBuffer() != null) {
            drawTree.getPageBuffer().unfix(root, GUIElements.VISUALIZER_OWNER);
        }
    }

    /**
     * NOTE: It is assumed that all pages have the same height.
     * 
     * @param depth depth, indexed from 0 (root is zero)
     * @param levelIndex page index at this depth level, starting from zero
     */
    public void drawTreeLevel(VisualizablePage<K, V> page, TreeDrawSurface<K, V> g, String title) {
        PositionInfo position = posMap.getPosition(page);
        assert position != null;
        if (position.isDrawn()) {
            return;
        }
        log.debug(String.format("Page %s, width: %d, at %s", page.getName(), position.getWidth(),
            position.getPosition()));
        drawPage(page, g, position, title);
        position.setDrawn();

        // Draw children
        List<VisualizablePage<K, V>> treeChildren = page.getChildren();

        // Draw children + parent links to children
        for (int i = 0; i < treeChildren.size(); i++) {
            VisualizablePage<K, V> treeChild = treeChildren.get(i);

            // posChildren.get(i);
            PositionInfo childPos = posMap.getPosition(treeChild);
            assert childPos != null;
            // Draw parent link to child pages
            g.drawPageParentLink(position, childPos, DRAW_X_OFFSET, yOffset);
            // Recurse: draw child page
            if (!childPos.isDrawn()) {
                drawTreeLevel(treeChild, g, null);
            }
        }
        // Draw links to peer pages
        if (page instanceof LinkPage<?, ?>) {
            @SuppressWarnings("unchecked")
            LinkPage<K, V> linkPage = (LinkPage<K, V>) page;
            for (Page<K, V> peer : linkPage.getLinks()) {
                // Draw link from this page to link
                PositionInfo peerPos = posMap.getPosition(peer);
                if (peerPos != null) {
                    g.drawPagePeerLink(position, peerPos, DRAW_X_OFFSET, yOffset);
                }
            }
        }
    }

    protected Color getPageParentLinkColor(Page<K, V> page) {
        Color color = null;
        if (page instanceof VisualPage) {
            VisualPage visPage = (VisualPage) page;
            color = visPage.getPageParentLinkColor(version, scheme);
        }
        return color != null ? color : scheme.getPageParentLinkColor();
    }

    protected Color getPageColor(Page<K, V> page) {
        Color color = null;
        if (page instanceof VisualPage) {
            VisualPage visPage = (VisualPage) page;
            color = visPage.getPageColor(version, scheme);
        }
        return color != null ? color : scheme.getPageBGColor();
    }

    protected TextLine[] getPageText(Page<K, V> page) {
        if (page instanceof VisualPage) {
            VisualPage visPage = (VisualPage) page;
            return visPage.getPageText(version, scheme);
        }
        return new TextLine[] {};
    }

    protected TextLine[] getPageName(Page<K, V> page) {
        if (page instanceof VisualPage) {
            VisualPage visPage = (VisualPage) page;
            return visPage.getPageName(version, scheme);
        }
        return new TextLine[] { new TextLine(page.getName()) };
    }

    /**
     * Draws page with the upper-left corner at x, y.
     */
    protected void drawPage(Page<K, V> page, TreeDrawSurface<K, V> g, PositionInfo posInfo,
        String treeName) {
        Point2D pagePos = posInfo.getPosition();
        int x = (int) (pagePos.getX() + DRAW_X_OFFSET);
        int y = (int) (pagePos.getY() + yOffset);
        Dimension size = posInfo.getPageSize();
        g.drawPage(page, x, y, size);

        // Tree name
        if (scheme.isShowTexts() && treeName != null) {
            g.drawTreeName(treeName, x + 5, y - scheme.getFontHeight() + 7);
        }
    }

}
