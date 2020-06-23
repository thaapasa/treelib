package fi.hut.cs.treelib.gui;

import java.awt.Graphics;
import java.io.OutputStream;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;

/**
 * Draws a multiversion tree as a (pseudo-)3D layer model in TikZ.
 * 
 * @author thaapasa
 */
public class TreeDrawTikZModel<K extends Key<K>, V extends PageValue<?>> extends
    TreeDrawGenericModel<K, V> {

    public TreeDrawTikZModel(Graphics dummy, OutputStream os) {
        super(dummy, os);
    }

    @Override
    protected String getComment(String s) {
        return "% " + s;
    }

    @Override
    protected String getPageLink(String key, String version, int childHeight) {
        return String.format("\\draw[llink] (%s,%d,%s) -- (%s,%d,%s);\n", version, childHeight,
            key, version, childHeight + 1, key);
    }

    @Override
    protected String getPageRectangle(String k1, String k2, String v1, String v2, int height,
        int pageId) {
        int pageStyle = pageId % 15 + 1;
        return String.format("\\fill[page,p%s,level%d] (%s,%d,%s) -- "
            + "(%s,%d,%s) -- (%s,%d,%s) -- (%s,%d,%s) -- cycle; %% Page %d\n", pageStyle, height,
            v1, height, k1, v1, height, k2, v2, height, k2, v2, height, k1, pageId);
    }

    @Override
    protected void printDocumentEnd() {
        target.println("\\end{tikzpicture}");
    }

    @Override
    protected void printDocumentStart() {
        target.println("\\begin{tikzpicture}[x=.32em,y=2.2em,z={(0.0038em,0.003em)}]");
    }

    @Override
    protected void printVersionRanges() {
        if (versionBounds.isInitialized()) {
            target.println(String.format("\\newcommand{\\versionmax}{%d}",
                versionBounds.getMax() + 1));
            target.println(String.format("\\newcommand{\\versionmin}{%d}",
                versionBounds.getMin() - 1));
        }
        if (keyBounds.isInitialized()) {
            target.println(String.format("\\newcommand{\\keymax}{%d}", keyBounds.getMax() + 1));
            target.println(String.format("\\newcommand{\\keymin}{%d}", keyBounds.getMin() - 1));
        }
    }

    @Override
    public String getPrintedKey(K key) {
        if (key.equals(key.getMaxKey())) {
            return "\\keymax";
        } else if (key.equals(key.getMinKey())) {
            return "\\keymin";
        }
        return String.valueOf(key.toInt());
    }

    @Override
    public String getPrintedVersion(int key) {
        if (key == Integer.MAX_VALUE) {
            return "\\versionmax";
        } else if (key == Integer.MIN_VALUE) {
            return "\\versionmin";
        }
        return String.valueOf(key);
    }

}
