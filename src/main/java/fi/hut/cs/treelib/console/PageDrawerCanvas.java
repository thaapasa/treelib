package fi.hut.cs.treelib.console;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import fi.hut.cs.treelib.Key;
import fi.tuska.util.media.gui.BufferedCanvas;

public class PageDrawerCanvas<K extends Key<K>> extends BufferedCanvas {

    private static final long serialVersionUID = 4449869121152521877L;

    private PageDrawer<K> drawer;

    public PageDrawerCanvas(PageDrawer<K> drawer) {
        super(300, 300, true);
        this.drawer = drawer;
    }

    @Override
    protected void drawBuffer(Graphics g, int width, int height) {
        Rectangle2D targetPos = new Rectangle(0, 0, width, height);
        drawer.drawPages(g, targetPos, getHeight());
    }

}
