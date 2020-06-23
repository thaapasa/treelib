package fi.hut.cs.treelib.gui;

import java.awt.Dimension;
import java.awt.FontMetrics;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.gui.PagePositionMap.PositionInfo;

public interface TreeDrawSurface<K extends Key<K>, V extends PageValue<?>> {

    void drawTreeTitle(String title);

    void clearTree(Dimension canvasSize);

    void drawPage(Page<K, V> page, int x, int y, Dimension size);

    void drawTreeName(String treeName, int x, int y);

    void drawPageParentLink(PositionInfo start, PositionInfo end, int xOffs, int yOffs);

    void drawPagePeerLink(PositionInfo start, PositionInfo end, int xOffs, int yOffs);
    
    FontMetrics getFontMetrics();
    
    void close();
    
}
