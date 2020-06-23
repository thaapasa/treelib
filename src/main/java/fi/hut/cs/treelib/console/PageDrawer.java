package fi.hut.cs.treelib.console;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDPage;
import fi.hut.cs.treelib.gui.GUIElements;
import fi.hut.cs.treelib.gui.PageMap;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.tuska.util.Pair;
import fi.tuska.util.geom.CoordinateUtils;
import fi.tuska.util.iterator.Iterables;
import fi.tuska.util.media.ColorUtils;
import fi.tuska.util.media.GraphicsUtils;

/**
 * Draws multidimensional pages and their contents. Used from the TreeConsole
 * to draw a visualization of the multidimensional tree's contents.
 * 
 * @author thaapasa
 */
public class PageDrawer<K extends Key<K>> {

    private static final Logger log = Logger.getLogger(PageDrawer.class);

    private LinkedList<MDPage<K, ?, ?>> pages;
    private MBR<K> extents;
    private Rectangle2D extentsRect;
    private PageBuffer buffer;

    private MBR<K> dbExtents;

    public PageDrawer(MBR<K> dbExtents, PageBuffer buffer) {
        this.pages = new LinkedList<MDPage<K, ?, ?>>();
        this.dbExtents = dbExtents;
        this.buffer = buffer;
        this.extents = null;
    }

    public void discard() {
        while (!pages.isEmpty()) {
            MDPage<K, ?, ?> p = pages.removeFirst();
            buffer.unfix(p, GUIElements.VISUALIZER_OWNER);
        }
    }

    public void addPage(MDPage<K, ?, ?> page) {
        if (pages.contains(page))
            return;

        log.debug("Adding page " + page + " to be drawn");
        MBR<K> pageMBR = page.getPageMBR();
        if (pageMBR == null) {
            log.info("Not adding page " + page + " as its MBR is not known");
            return;
        }

        pages.add(page);
        extents = extents != null ? extents.extend(pageMBR) : pageMBR;
        extentsRect = mbrToRect(extents);
    }

    public void clear() {
        pages.clear();
        this.extents = null;
    }

    public void drawPages(Graphics g, Rectangle2D targetPosition, int targetMaxY) {
        for (MDPage<K, ?, ?> page : pages) {
            Color color = PageMap.idToColor(page.getPageID());
            Color borderColor = ColorUtils.darken(color, 0.5);
            Rectangle2D coords = getPageCoords(page.getPageMBR(), targetPosition);
            if (page.isLeafPage()) {
                GraphicsUtils.drawRect(coords, targetMaxY, borderColor, g);
            } else {
                GraphicsUtils.dashedRect(coords, targetMaxY, borderColor, g);
            }
            drawContents(page, color, targetPosition, targetMaxY, g);
        }
    }

    private <L extends Key<L>> void drawContents(MDPage<K, ?, L> page, Color color,
        Rectangle2D targetPosition, int targetMaxY, Graphics g) {
        Color bgColor = ColorUtils.lighten(color, 0.4);
        List<Rectangle2D> drawMBRs = new ArrayList<Rectangle2D>();
        for (Pair<L, MBR<K>> key : Iterables.get(page.getUniqueKeys())) {
            MBR<K> drawMBR = null;
            if (page.isLeafPage()) {
                drawMBR = key.getSecond();
            } else {
                MDPage<K, ?, ?> child = page.getUniqueChild(key.getFirst(), key.getSecond(),
                    GUIElements.VISUALIZER_OWNER);
                assert child != null : page + ", key " + key;
                drawMBR = child.getPageMBR();
                buffer.unfix(child, GUIElements.VISUALIZER_OWNER);
            }
            drawMBRs.add(getPageCoords(drawMBR, targetPosition));
        }
        for (Rectangle2D coords : drawMBRs) {
            GraphicsUtils.fillRect(coords, targetMaxY, bgColor, g);
        }
        for (Rectangle2D coords : drawMBRs) {
            GraphicsUtils.drawRect(coords, targetMaxY, color, g);
        }
    }

    public Rectangle2D getPageCoords(MBR<K> pageMBR, Rectangle2D target) {
        Rectangle2D pageRect = mbrToRect(pageMBR);
        int pad = 5;

        Rectangle2D imageTarget = new Rectangle((int) target.getX() + pad, (int) target.getY()
            + pad, (int) target.getWidth() - 2 * pad, (int) target.getHeight() - 2 * pad);
        return CoordinateUtils.scaleRect(pageRect, extentsRect, imageTarget);
    }

    private Rectangle2D mbrToRect(MBR<K> mbr) {
        if (mbr == null)
            return null;
        if (mbr.getDimensions() != 2)
            throw new UnsupportedOperationException(
                "Does not support other than 2-dimensional MBRs");

        float x1 = mbr.getLow(0).toFloat();
        float x2 = mbr.getHigh(0).toFloat();
        float y1 = mbr.getLow(1).toFloat();
        float y2 = mbr.getHigh(1).toFloat();
        if (dbExtents != null) {
            x1 = Math.max(x1, dbExtents.getLow(0).toFloat());
            x2 = Math.min(x2, dbExtents.getHigh(0).toFloat());
            y1 = Math.max(y1, dbExtents.getLow(1).toFloat());
            y2 = Math.min(y2, dbExtents.getHigh(1).toFloat());
        }
        return new Rectangle2D.Float(x1, y1, x2 - x1, y2 - y1);
    }

}
