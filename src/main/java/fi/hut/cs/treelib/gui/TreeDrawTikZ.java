package fi.hut.cs.treelib.gui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.io.OutputStream;
import java.io.PrintStream;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.gui.PagePositionMap.PositionInfo;
import fi.hut.cs.treelib.gui.VisualPage.TextLine;

/**
 * Draws the tree as a TikZ picture. Shows the pages as rectangles, and draws
 * the page contents as textual items. The pages are linked with child-parent
 * links (and peer links, where applicable).
 * 
 * @author thaapasa
 */
public class TreeDrawTikZ<K extends Key<K>, V extends PageValue<?>> implements
    TreeDrawSurface<K, V> {

    private final TreeDrawStyle scheme;
    private final int version;

    private OutputStream os;
    private PrintStream target;
    private FontMetrics metrics;
    private StringBuilder drawnLinks = new StringBuilder();

    public TreeDrawTikZ(Graphics dummy, TreeDrawStyle scheme, int version, OutputStream os) {
        this.scheme = scheme;
        this.version = version;
        this.os = os;
        this.target = new PrintStream(os);
        this.metrics = new DummyFontMetrics();
    }

    protected TextLine[] getPageName(Page<K, V> page) {
        if (page instanceof VisualPage) {
            VisualPage visPage = (VisualPage) page;
            return visPage.getPageName(version, scheme);
        }
        return new TextLine[] { new TextLine(page.getName()) };
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
        target.println("\\node[title] {" + title + "};");
    }

    @Override
    public void drawTreeName(String treeName, int x, int y) {
        target.println("\\node[treename] at (" + x + "," + y + ") {" + treeName + "};");
    }

    @Override
    public void clearTree(Dimension canvasSize) {
        target.flush();
        drawnLinks.setLength(0);
        target.println("\\begin{tikzpicture}[x=0.108em,y=-0.058em]");
    }

    public String getValStr(String s) {
        return s.replace(IntegerKey.MAX_KEY_STR, "\\infty");
    }

    public String getKeyRangeString(KeyRange<?> kr) {
        String b = kr.getMin().toString();
        String e = kr.getMax().toString();
        return String.format("[%s%s,\\!%s%s)", b.startsWith("-") ? "\\!" : "", getValStr(b),
            getValStr(e), e.equals(IntegerKey.MAX_KEY_STR) ? "\\!" : "");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void drawPage(Page<K, V> page, int x, int y, Dimension size) {

        int id = page.getPageID().getValue();
        String heightInLines = "6.5";

        String pageStyle = "treepage";
        KeyRange<K> kr = page.getKeyRange();
        if (kr instanceof MVKeyRange) {
            MVKeyRange<K> mvKR = (MVKeyRange<K>) kr;
            if (!mvKR.containsVersion(version)) {
                pageStyle += ",dead";
            }
        }
        target.println(String.format("\\node[%s] (p%d) at (%d,%d) {%%", pageStyle, id, x, y));
        String contentsFunc = page.isLeafPage() ? "treepagecontents" : "treeindexpagecontents";
        if (kr instanceof MVKeyRange) {
            MVKeyRange<K> mvKR = (MVKeyRange<K>) kr;
            target.println(String.format("\\%s{%s}{$%s,%s$}%%", contentsFunc, heightInLines,
                getKeyRangeString(mvKR), getKeyRangeString(mvKR.getVersionRange())));
        } else {
            target.println(String.format("\\%s{%s}{$%s$}%%", contentsFunc, heightInLines,
                getKeyRangeString(kr)));
        }
        target.println("  {%%");

        TextLine[] text = getPageText(page);
        for (int i = 0; i < text.length; i++) {
            if (i != 0)
                target.println("\\\\");
            TextLine line = text[i];
            if (line.keyRange != null && line.value != null) {
                if (line.keyRange instanceof MVKeyRange) {
                    MVKeyRange<K> mvKR = (MVKeyRange<K>) line.keyRange;
                    if (page.isLeafPage()) {
                        target.print(String.format("   $(%s,%s)$", getValStr(line.keyRange
                            .getMin().toString()), getKeyRangeString(mvKR.getVersionRange())));
                    } else {
                        int cid = ((PageID) (line.value)).intValue();
                        target.print(String.format("   $(%s,%s,p_{%d})$",
                            getKeyRangeString(mvKR), getKeyRangeString(mvKR.getVersionRange()),
                            cid));
                    }
                } else {
                    if (page.isLeafPage()) {
                        target.print(String.format("   $%s$", getValStr(line.keyRange.getMin()
                            .toString())));
                    } else {
                        int cid = ((PageID) (line.value)).intValue();
                        target.print(String.format("   $(%s,p_{%d})$",
                            getKeyRangeString(line.keyRange), cid));
                    }
                }
            } else {
                target.print("   $" + line.getText() + "$");
            }
        }
        target.println("}};");
        target.println(String.format("\\treepageheader{p%d}{$p_{%d}$};", id, id));
    }

    @Override
    public void drawPageParentLink(PositionInfo start, PositionInfo end, int xOffs, int yOffs) {
        Page<?, ?> s = start.getPage();
        Page<?, ?> e = end.getPage();

        drawnLinks.append(String.format(
            "\\draw[parentlink] (p%d.south) -- ($(p%dheader.north) + (0,-2)$);\n", s.getPageID()
                .getValue(), e.getPageID().getValue()));
    }

    @Override
    public void drawPagePeerLink(PositionInfo start, PositionInfo end, int xOffs, int yOffs) {
        Page<?, ?> s = start.getPage();
        Page<?, ?> e = end.getPage();

        drawnLinks.append(String.format("\\draw[peerlink] (p%d) -- (p%d);\n", s.getPageID()
            .getValue(), e.getPageID().getValue()));
    }

    @Override
    public FontMetrics getFontMetrics() {
        return metrics;
    }

    public OutputStream getOutputStream() {
        return os;
    }

    @Override
    public void close() {
        target.println(drawnLinks);
        target.println("\\end{tikzpicture}");
        target.flush();
    }

}
