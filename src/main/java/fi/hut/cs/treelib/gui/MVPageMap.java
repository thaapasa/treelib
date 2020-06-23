package fi.hut.cs.treelib.gui;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVPage;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.VisualizableTree;
import fi.tuska.util.geom.CoordinateUtils;
import fi.tuska.util.media.GraphicsUtils;

/**
 * Map and drawer for showing a 2D visualization of page contents. Shows
 * multiversion pages, rendered as rectangles in key-version space.
 * 
 * @author thaapasa
 */
public class MVPageMap<K extends Key<K>, V extends PageValue<?>> extends PageMap<K, V> {

    private static final Logger log = Logger.getLogger(MVPageMap.class);
    private static final long serialVersionUID = -5644032303427032318L;

    private MVTree<K, V, ?> multiTree;
    private VisualizableTree<K, V, ?> shownTree;
    private Integer shownVersion;

    @SuppressWarnings("unchecked")
    public <P extends Page<K, V>> MVPageMap(MVTree<K, V, P> tree, K keyPrototype, int width,
        int height) {
        super((VisualizableTree<K, V, P>) tree, keyPrototype, width, height);
        this.multiTree = tree;
        this.shownTree = (VisualizableTree<K, V, P>) tree;
    }

    @SuppressWarnings("unchecked")
    public void setShownVersion(Integer version) {
        log.debug("Setting shown version to " + version);
        if (version == null) {
            shownVersion = null;
            shownTree = (VisualizableTree<K, V, ?>) multiTree;
        } else {
            shownVersion = version;
            if (shownVersion < 0) {
                shownVersion = new Integer(0);
            }
            if (shownVersion > multiTree.getLatestVersion()) {
                shownVersion = multiTree.getLatestVersion();
            }
            shownTree = (VisualizableTree<K, V, ?>) multiTree.getVersionTree(shownVersion);
        }
    }

    public Integer getShownVersion() {
        return shownVersion;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void paint(Graphics g) {
        g.setColor(BG_COLOR);
        g.fillRect(0, 0, width, height);
        updateExtents();

        if (shownVersion != null) {
            drawTree((VisualizableTree<K, V, ?>) multiTree, false, g);
        }
        drawTree(shownTree, true, g);
        if (shownVersion != null) {
            drawVersionPosition(g);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void drawTree(VisualizableTree<K, V, ?> tree, boolean colorized, Graphics g) {
        if (tree instanceof MVTree) {
            drawMultiTree((MVTree<K, V, ?>) tree, colorized, g);
        } else {
            super.drawTree(tree, colorized, g);
        }
    }

    protected void drawVersionPosition(Graphics g) {
        MVKeyRange<K> treeRange = multiTree.getKeyRange();
        MVKeyRange<K> range = new MVKeyRange<K>(treeRange.getMin(), treeRange.getMax(),
            shownVersion, shownVersion + 1);
        Rectangle2D rect = multiVersionKeyRangeToKVR(range);

        Rectangle2D coords = CoordinateUtils.scaleRect(rect, extents, targetRect);
        coords = CoordinateUtils.clipRect(coords, targetRect);

        GraphicsUtils.dashedRect(coords, targetRect.getMaxY(), PAGE_BORDER_COLOR, g);
    }

    @Override
    public Page<K, V> getPageByScreenCoords(int x, int y, Owner owner) {
        Point2D point = logicalToKVR(new Point(x, (int) targetRect.getMaxY() - y));
        int keyInt = (int) point.getX();
        K key = keyPrototype.fromInt(keyInt);
        int version = (int) point.getY();

        log.info(String.format("Searching for key %s at version %d", key, version));
        return multiTree.getPage(key, version, owner);
    }

    @Override
    protected void updateExtents() {
        MVKeyRange<K> treeRange = multiTree.getKeyRange();
        extents = multiVersionKeyRangeToKVR(treeRange);
        extents = CoordinateUtils.extendRect(extents, minRange);
    }

    @SuppressWarnings("unchecked")
    protected void drawMultiTree(MVTree<K, V, ?> multiTree, boolean colorized, Graphics g) {
        MVKeyRange<K> treeRange = multiTree.getKeyRange();
        Collection<VisualizablePage<K, V>> pages = ((VisualizableTree<K, V, ?>) multiTree)
            .getPagesAtHeight(level);
        if (pages == null || treeRange == null) {
            return;
        }

        for (Page<K, V> page : pages) {
            MVPage<K, V, ?> multiPage = (MVPage<K, V, ?>) page;
            drawMultiPage(multiPage, colorized, g);
        }
    }

    protected void drawMultiPage(MVPage<K, V, ?> page, boolean colorized, Graphics g) {
        Rectangle2D object = multiVersionKeyRangeToKVR(page.getKeyRange());
        Rectangle2D coords = kvrToLogical(object);
        drawPage(coords, page, page.getShortName(), colorized, g);
    }

    public int getVersionByScreenCoords(int x, int y) {
        Point2D point = logicalToKVR(new Point(x, (int) targetRect.getMaxY() - y));
        return (int) point.getY();
    }

    /**
     * Converts a key-version range into a rectangle. The key values will be
     * on the X axis, and the version values along the Y axis.
     * 
     * @param range the key-version range to convert
     * @return the key range converted to Key-Version-Range rectangle
     */
    private Rectangle2D multiVersionKeyRangeToKVR(MVKeyRange<K> range) {
        int maxV = Math.min(range.getMaxVersion().intValue(), MAX_KEY);
        int minV = Math.max(range.getMinVersion().intValue(), MIN_KEY);
        int max = Math.min(range.getMax().toInt(), MAX_KEY);
        int min = Math.max(range.getMin().toInt(), MIN_KEY);
        return new Rectangle(min, minV, max - min, maxV - minV);
    }

    @Override
    public String toString() {
        return String.format("Level: %d, version: %s", level, shownVersion != null ? shownVersion
            .toString() : "all");
    }

}
