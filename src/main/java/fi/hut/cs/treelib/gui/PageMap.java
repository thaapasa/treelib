package fi.hut.cs.treelib.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.util.Collection;

import javax.swing.JComponent;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.VisualizableTree;
import fi.tuska.util.geom.CoordinateUtils;
import fi.tuska.util.media.ColorUtils;
import fi.tuska.util.media.GraphicsUtils;

/**
 * Map and drawer for showing a 2D visualization of multiversion page
 * contents.
 * 
 * @author thaapasa
 */
public class PageMap<K extends Key<K>, V extends PageValue<?>> extends JComponent {

    private static final Logger log = Logger.getLogger(PageMap.class);

    private static final long serialVersionUID = 9157310424896798569L;

    protected static final Color BG_COLOR = new Color(255, 255, 255);
    protected static final Color PAGE_BORDER_COLOR = new Color(32, 32, 128);
    protected static final Color PAGE_TEXT_COLOR = new Color(0, 0, 0);
    protected final Rectangle2D minRange;
    protected final K keyPrototype;

    protected static final int MIN_KEY = -1000;
    protected static final int MAX_KEY = 1000;

    private VisualizableTree<K, V, ?> tree;
    protected int width;
    protected int height;
    protected int level;
    protected Rectangle2D targetRect;
    protected Rectangle2D extents;
    private BufferedImage buffer;

    public PageMap(VisualizableTree<K, V, ?> tree, K keyPrototype, int width, int height) {
        this.tree = tree;
        this.keyPrototype = keyPrototype;
        this.width = width;
        this.height = height;
        this.level = 1;
        this.targetRect = new Rectangle(0, 0, width - 1, height - 1);

        if (tree instanceof MVTree<?, ?, ?>) {
            minRange = new Rectangle(0, 1, 1, 1);
        } else {
            minRange = new Rectangle(0, 0, 1, 1);
        }

        Dimension size = new Dimension(width, height);
        setSize(size);
        setMinimumSize(size);
        setPreferredSize(size);
        setMaximumSize(size);
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setTree(VisualizableTree<K, V, ?> tree) {
        this.tree = tree;
    }

    public Page<K, V> getPageByScreenCoords(int x, int y, Owner owner) {
        Point2D point = logicalToKVR(new Point(x, (int) targetRect.getMaxY() - y));
        int keyInt = (int) point.getX();
        K key = keyPrototype.fromInt(keyInt);
        log.info(String.format("Searching for key %s", key));
        return tree.getPage(key, owner);
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    @Override
    public void paint(Graphics g) {
        if (buffer != null) {
            g.drawImage(buffer, 0, 0, this);
        }
    }

    public RenderedImage getImage() {
        return buffer;
    }

    public void update() {
        buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        updateExtents();

        Graphics g = buffer.getGraphics();

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, width, height);

        updateExtents();
        drawTree(tree, true, g);
        repaint();
    }

    protected void updateExtents() {
        KeyRange<K> treeRange = tree.getKeyRange();
        extents = keyRangeToKVR(treeRange);
        extents = CoordinateUtils.extendRect(extents, minRange);
    }

    protected void drawTree(VisualizableTree<K, V, ?> tree, boolean colorized, Graphics g) {
        Collection<VisualizablePage<K, V>> pages = tree.getPagesAtHeight(level);
        KeyRange<K> treeRange = tree.getKeyRange();
        if (pages == null || treeRange == null) {
            return;
        }

        for (Page<K, V> page : pages) {
            drawPage(page, colorized, g);
        }
    }

    protected void drawPage(Page<K, V> page, boolean colorized, Graphics g) {
        Rectangle2D object = keyRangeToKVR(page.getKeyRange());
        Rectangle2D coords = kvrToLogical(object);
        drawPage(coords, page, page.getShortName(), colorized, g);
    }

    /**
     * Coordinates are in mathematically logical coordinate space, e.g.:
     * 
     * <pre>
     * +y
     *  |
     *  |
     *  0----- +x
     * </pre>
     */
    protected void drawPage(Rectangle2D coords, Page<K, V> page, String text, boolean colorized,
        Graphics g) {
        log.debug(String.format("Drawing page %s to rect %s", text, coords.toString()));
        GraphicsUtils.fillRect(coords, targetRect.getMaxY(), getColor(
            idToColor(page.getPageID()), colorized), g);
        GraphicsUtils.drawRect(coords, targetRect.getMaxY(), getColor(PAGE_BORDER_COLOR,
            colorized), g);
        g.setColor(getColor(PAGE_TEXT_COLOR, colorized));
        g.drawString(text, (int) coords.getMinX() + 5, (int) (targetRect.getMaxY() - coords
            .getMaxY()) + 13);
    }

    protected Color getColor(Color color, boolean colorized) {
        return colorized ? color : ColorUtils.lighten(ColorUtils.toMonochrome(color), 0.7);
    }

    public static Color idToColor(PageID pageID) {
        int id = pageID.intValue();
        int r = 120 + (id * 81) % 127;
        int g = 255 - (id * 17) % 120;
        int b = 120 + ((id + 125) * 117) % 127;

        assert r >= 0 : r;
        assert r <= 255 : r;
        assert g >= 0 : g;
        assert g <= 255 : g;
        assert b >= 0 : b;
        assert b <= 255 : b;

        return new Color(r, g, b);
    }

    /**
     * Converts a key range to a rectangle. The key values will be along the X
     * axis. The width along the Y-axis will be 1.
     * 
     * @param range the key range to convert
     * @return key range converted to Key-Version-Range rectangle
     */
    private Rectangle2D keyRangeToKVR(KeyRange<K> range) {
        K min = range.getMin();
        K max = range.getMax();
        int minI = Math.max(min.toInt(), MIN_KEY);
        int maxI = Math.min(max.toInt(), MAX_KEY);

        return new Rectangle(minI, 0, maxI - minI, 1);
    }

    protected Rectangle2D kvrToLogical(Rectangle2D object) {
        Rectangle2D coords = CoordinateUtils.scaleRect(object, extents, targetRect);
        return CoordinateUtils.clipRect(coords, targetRect);
    }

    protected Rectangle2D logicalToKVR(Rectangle2D object) {
        Rectangle2D coords = CoordinateUtils.scaleRect(object, targetRect, extents);
        return coords;
    }

    protected Point2D logicalToKVR(Point logical) {
        Rectangle2D logicalR = new Rectangle(logical);
        Rectangle2D kvrR = logicalToKVR(logicalR);
        return new Point((int) kvrR.getMinX(), (int) kvrR.getMinY());
    }

    @Override
    public String toString() {
        return String.format("Level: %d", level);
    }
}
