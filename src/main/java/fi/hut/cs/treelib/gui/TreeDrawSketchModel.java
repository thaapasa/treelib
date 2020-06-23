package fi.hut.cs.treelib.gui;

import java.awt.Graphics;
import java.io.OutputStream;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;

/**
 * Draws a multiversion tree as a Sketch 3d definition file.
 * 
 * @author thaapasa
 */
public class TreeDrawSketchModel<K extends Key<K>, V extends PageValue<?>> extends
    TreeDrawGenericModel<K, V> {

    public TreeDrawSketchModel(Graphics dummy, OutputStream os) {
        super(dummy, os);
    }

    @Override
    protected String getComment(String s) {
        return "";
    }

    @Override
    protected String getPageLink(String key, String version, int childHeight) {
        return String.format("line[line style=llink](%s,%d,%s)(%s,%d,%s)\n", version,
            childHeight, key, version, childHeight + 1, key);
    }

    @Override
    protected String getPageRectangle(String k1, String k2, String v1, String v2, int height,
        int pageId) {
        int pageStyle = pageId % 15 + 1;

        return String.format(
            "polygon[fill style=page,fill style=level%d,fill style=p%s](%s,%d,%s)"
                + "(%s,%d,%s)(%s,%d,%s)(%s,%d,%s)\n", height, pageStyle, v1, height, k1, v1,
            height, k2, v2, height, k2, v2, height, k1);
    }

    @Override
    protected void printDocumentEnd() {
        target.println("}");
        target.println("");
        target.println("global{language tikz}");
    }

    @Override
    protected void printDocumentStart() {
    }

    @Override
    protected void printVersionRanges() {

        if (versionBounds.isInitialized()) {
            target.println(String.format("def versionmax %d", versionBounds.getMax() + 1));
            target.println(String.format("def versionmin %d", versionBounds.getMin() - 1));
        }
        if (keyBounds.isInitialized()) {
            target.println(String.format("def keymax %d", keyBounds.getMax() + 1));
            target.println(String.format("def keymin %d", keyBounds.getMin() - 1));
        }

        target.println();

        target.println("def eye (3.5,3.2,9)");
        target.println("def look_at (6,1.0,3)");
        target.println("def up [-.08,1,0]");
        target.println("def persp_scale 7.4");
        target.println();
        target.println("put {");
        target.println("  scale([.1,.9,.004]) then");
        target.println("  view((eye),(look_at),[up]) then");
        target.println("  perspective(persp_scale)");
        target.println("}");
        target.println("{");
    }

    @Override
    public String getPrintedKey(K key) {
        if (key.equals(key.getMaxKey())) {
            return "keymax";
        } else if (key.equals(key.getMinKey())) {
            return "keymin";
        }
        return String.valueOf(key.toInt());
    }

    @Override
    public String getPrintedVersion(int key) {
        if (key == Integer.MAX_VALUE) {
            return "versionmax";
        } else if (key == Integer.MIN_VALUE) {
            return "versionmin";
        }
        return String.valueOf(key);
    }

}
