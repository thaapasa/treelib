package fi.hut.cs.treelib.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.geom.Point2D;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.gui.PagePositionMap.PositionInfo;
import fi.hut.cs.treelib.gui.VisualPage.TextLine;

public class TreeDrawGraphics<K extends Key<K>, V extends PageValue<?>> implements
    TreeDrawSurface<K, V> {

    private final Graphics target;
    private final TreeDrawStyle scheme;
    private final int version;
    private FontMetrics metrics;

    public TreeDrawGraphics(Graphics target, TreeDrawStyle scheme, int version) {
        this.target = target;
        this.scheme = scheme;
        this.version = version;
        this.metrics = target.getFontMetrics();
    }

    protected TextLine[] getPageName(Page<K, V> page) {
        if (page instanceof VisualPage) {
            VisualPage visPage = (VisualPage) page;
            return visPage.getPageName(version, scheme);
        }
        return new TextLine[] { new TextLine(page.getName()) };
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

    @Override
    public void drawTreeTitle(String title) {
        target.setColor(scheme.getTitleColor());
        target.drawString(title, 20, 20);
    }

    @Override
    public void drawTreeName(String treeName, int x, int y) {
        target.setColor(Color.BLACK);
        target.drawString(treeName, x, y);
    }

    @Override
    public void clearTree(Dimension canvasSize) {
        target.setColor(scheme.getBgColor());
        target.fillRect(0, 0, (int) canvasSize.getWidth(), (int) canvasSize.getHeight());

        target.setFont(scheme.getFont());
    }

    @Override
    public void drawPage(Page<K, V> page, int x, int y, Dimension size) {
        target.setColor(getPageColor(page));
        target.fillRect(x, y, (int) size.getWidth(), (int) size.getHeight());
        target.setColor(scheme.getPageBorderColor());
        target.drawRect(x, y, (int) size.getWidth(), (int) size.getHeight());

        int row = 1;
        // Title
        {
            TextLine[] headers = getPageName(page);
            for (TextLine header : headers) {
                target.setColor(header.getTextColor(scheme.getPageTextColor()));
                target.drawString(header.getText(), x + 5, y + row * scheme.getFontHeight());
                row++;
            }
        }
        target.drawLine(x, y + (row - 1) * scheme.getFontHeight() + 5, x + (int) size.getWidth(),
            y + (row - 1) * scheme.getFontHeight() + 5);

        TextLine[] text = getPageText(page);
        for (int i = 0; i < text.length; i++) {
            TextLine line = text[i];
            Color textColor = line.getTextColor(scheme.getPageTextColor());
            target.setColor(textColor);
            target.drawString(line.getText(), x + 5, y + (row * scheme.getFontHeight()) + 4);
            row++;
        }
    }

    protected Color getPageParentLinkColor(Page<?, ?> page) {
        Color color = null;
        if (page instanceof VisualPage) {
            VisualPage visPage = (VisualPage) page;
            color = visPage.getPageParentLinkColor(version, scheme);
        }
        return color != null ? color : scheme.getPageParentLinkColor();
    }

    @Override
    public void drawPageParentLink(PositionInfo start, PositionInfo end, int xOffs, int yOffs) {
        Point2D startPos = start.getParentLinkStartPos();
        Point2D endPos = end.getParentLinkEndPos();

        Color c = getPageParentLinkColor(start.getPage());
        target.setColor(c);
        int x1 = (int) startPos.getX() + xOffs;
        int y1 = (int) startPos.getY() + yOffs;
        int x2 = (int) endPos.getX() + xOffs;
        int y2 = (int) endPos.getY() + yOffs;
        target.drawLine(x1, y1, x2, y2);
    }

    @Override
    public void drawPagePeerLink(PositionInfo start, PositionInfo end, int xOffs, int yOffs) {
        Point2D startPos = start.getParentLinkStartPos();
        Point2D endPos = end.getParentLinkEndPos();

        target.setColor(scheme.getPagePeerLinkColor());
        int x1 = (int) startPos.getX() + xOffs;
        int y1 = (int) startPos.getY() + yOffs;
        int x2 = (int) endPos.getX() + xOffs;
        int y2 = (int) endPos.getY() + yOffs;
        target.drawLine(x1, y1, x2, y2);

        target.drawLine(x2, y2, x2 - 4, y2 - 3);
        target.drawLine(x2, y2, x2 - 4, y2 + 3);
    }

    @Override
    public FontMetrics getFontMetrics() {
        return metrics;
    }

    @Override
    public void close() {
        // Nothing required
    }

}
