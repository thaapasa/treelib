package fi.hut.cs.treelib.gui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.gui.PagePositionMap.PositionInfo;
import fi.tuska.util.Triple;
import fi.tuska.util.math.MinMaxInt;

/**
 * Draws a multiversion tree as some kind of a model (defined in the
 * subclass). The drawing methods just collect the data, so call the close()
 * method to actually output the model.
 * 
 * @author thaapasa
 */
public abstract class TreeDrawGenericModel<K extends Key<K>, V extends PageValue<?>> implements
    TreeDrawSurface<K, V> {

    protected static boolean DRAW_PARENT_CHILD_LINKS = false;
    protected static boolean DRAW_PAGE_EXTENTS_LINKS = true;

    protected final MinMaxInt versionBounds = new MinMaxInt(false);
    protected final MinMaxInt keyBounds = new MinMaxInt(false);

    private OutputStream os;
    private FontMetrics metrics;
    protected final PrintStream target;
    protected SortedMap<Integer, StringBuilder> treeLevels = new TreeMap<Integer, StringBuilder>();

    protected Set<Triple<Integer, Integer, Integer>> drawnLinks = new HashSet<Triple<Integer, Integer, Integer>>();
    protected Set<Integer> drawnPages = new HashSet<Integer>();

    public TreeDrawGenericModel(Graphics dummy, OutputStream os) {
        this.os = os;
        this.target = new PrintStream(os);
        this.metrics = new DummyFontMetrics();
    }

    @Override
    public final void drawTreeTitle(String title) {
        // Do nothing
    }

    @Override
    public final void drawTreeName(String treeName, int x, int y) {
        // Do nothing
    }

    @Override
    public final void clearTree(Dimension canvasSize) {
        target.flush();
        treeLevels.clear();
        drawnPages.clear();
        drawnLinks.clear();
        printDocumentStart();
    }

    protected abstract void printDocumentStart();

    public abstract String getPrintedKey(K key);

    public abstract String getPrintedVersion(int key);

    protected final StringBuilder getTreeLevelStringBuilder(int level) {
        // Draw page
        StringBuilder b = treeLevels.get(level);
        if (b == null) {
            b = new StringBuilder();
            treeLevels.put(level, b);
        }
        return b;
    }

    @Override
    public final void drawPage(Page<K, V> page, int x, int y, Dimension size) {
        KeyRange<K> kr = page.getKeyRange();
        if (!(kr instanceof MVKeyRange<?>)) {
            // Cannot draw this
            return;
        }

        int pageId = page.getPageID().intValue();
        if (!drawnPages.add(pageId))
            return;
        MVKeyRange<K> mvKR = (MVKeyRange<K>) kr;

        // Get page bounds
        int v1 = mvKR.getMinVersion();
        int v2 = mvKR.getMaxVersion();
        K k1 = mvKR.getMin();
        K k2 = mvKR.getMax();

        int h = page.getHeight();

        // Update min & max values
        versionBounds.process(v1);
        versionBounds.process(v2);
        keyBounds.process(k1.toInt());
        keyBounds.process(k2.toInt());

        // Draw page
        StringBuilder b = getTreeLevelStringBuilder(h * 2);

        b.append(getPageRectangle(getPrintedKey(k1), getPrintedKey(k2), getPrintedVersion(v1),
            getPrintedVersion(v2), h, pageId));

        if (DRAW_PAGE_EXTENTS_LINKS && !page.isLeafPage()) {
            StringBuilder b1 = getTreeLevelStringBuilder(h * 2 - 1);
            drawLink(k1, v1, h - 1, b1);
            drawLink(k1, v2, h - 1, b1);
            drawLink(k2, v2, h - 1, b1);
            drawLink(k2, v1, h - 1, b1);
        }
    }

    protected abstract String getPageRectangle(String k1, String k2, String v1, String v2,
        int height, int pageId);

    @Override
    @SuppressWarnings("unchecked")
    public final void drawPageParentLink(PositionInfo parent, PositionInfo child, int xOffs,
        int yOffs) {
        if (!DRAW_PARENT_CHILD_LINKS)
            return;
        Page<?, ?> p = parent.getPage();
        Page<?, ?> c = child.getPage();
        assert p.getHeight() > c.getHeight();

        int h = c.getHeight();
        KeyRange<?> kr = c.getKeyRange();
        if (!(kr instanceof MVKeyRange<?>)) {
            // Cannot draw this
            return;
        }
        MVKeyRange<?> mvKR = (MVKeyRange<?>) kr;

        StringBuilder b = getTreeLevelStringBuilder(h * 2 + 1);
        drawLink((K) mvKR.getMin(), mvKR.getMinVersion(), h, b);
        drawLink((K) mvKR.getMax(), mvKR.getMinVersion(), h, b);
        drawLink((K) mvKR.getMin(), mvKR.getMaxVersion(), h, b);
        drawLink((K) mvKR.getMax(), mvKR.getMaxVersion(), h, b);
    }

    protected final void drawLink(K key, int version, int childHeight, StringBuilder b) {
        if (drawnLinks.add(new Triple<Integer, Integer, Integer>(key.toInt(), version,
            childHeight))) {
            b.append(getPageLink(getPrintedKey(key), getPrintedVersion(version), childHeight));
        }
    }

    protected abstract String getPageLink(String key, String version, int childHeight);

    @Override
    public final void drawPagePeerLink(PositionInfo start, PositionInfo end, int xOffs, int yOffs) {
        // Nada
    }

    @Override
    public final FontMetrics getFontMetrics() {
        return metrics;
    }

    public final OutputStream getOutputStream() {
        return os;
    }

    protected abstract void printDocumentEnd();

    protected abstract void printVersionRanges();

    protected abstract String getComment(String s);

    @Override
    public final void close() {
        printVersionRanges();
        target.println();
        for (int h : treeLevels.keySet()) {
            target.println(getComment(h % 2 == 0 ? ("Level " + h / 2) : "Parent-child links"));
            StringBuilder b = treeLevels.get(h);
            target.println(b);
        }

        printDocumentEnd();
        target.flush();
    }

}
