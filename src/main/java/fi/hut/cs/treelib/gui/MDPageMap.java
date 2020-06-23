package fi.hut.cs.treelib.gui;

import java.awt.Graphics;
import java.awt.geom.Rectangle2D;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDPage;
import fi.hut.cs.treelib.MDTree;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.VisualizableTree;
import fi.tuska.util.geom.CoordinateUtils;

/**
 * Map and drawer for showing a 2D visualization of page contents.
 * 
 * @author thaapasa
 */
public class MDPageMap<K extends Key<K>, V extends PageValue<?>> extends PageMap<MBR<K>, V> {

    private static final long serialVersionUID = 8961930388493017366L;

    private static final Logger log = Logger.getLogger(MDPageMap.class);

    private MDTree<K, V, MDPage<K, V, ?>> tree;

    @SuppressWarnings("unchecked")
    public MDPageMap(VisualizableTree<MBR<K>, V, ?> tree, MBR<K> keyPrototype, int width,
        int height) {
        super(tree, keyPrototype, width, height);
        this.tree = (MDTree<K, V, MDPage<K, V, ?>>) tree;
    }

    @Override
    protected void updateExtents() {
        MDPage<K, V, ?> root = tree.getRoot(GUIElements.VISUALIZER_OWNER);
        if (root != null) {
            extents = mbrToKVR(root.getPageMBR());
            extents = CoordinateUtils.extendRect(extents, minRange);
            tree.getPageBuffer().unfix(root, GUIElements.VISUALIZER_OWNER);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void drawPage(Page<MBR<K>, V> page, boolean colorized, Graphics g) {
        MDPage<K, V, ?> mdPage = (MDPage<K, V, ?>) page;

        Rectangle2D object = mbrToKVR(mdPage.getPageMBR());
        Rectangle2D coords = kvrToLogical(object);
        log.debug(String.format("Page MBR: %s, object: %s, coords: %s", mdPage.getPageMBR(),
            object, coords));
        drawPage(coords, page, page.getShortName(), colorized, g);
    }

    private Rectangle2D mbrToKVR(MBR<K> mbr) {
        K minX = mbr.getLow(0);
        K maxX = mbr.getHigh(0);
        float minXI = Math.max(minX.toFloat(), MIN_KEY);
        float maxXI = Math.min(maxX.toFloat(), MAX_KEY);
        K minY = mbr.getLow(1);
        K maxY = mbr.getHigh(1);
        float minYI = Math.max(minY.toFloat(), MIN_KEY);
        float maxYI = Math.min(maxY.toFloat(), MAX_KEY);

        return new Rectangle2D.Float(minXI, minYI, maxXI - minXI, maxYI - minYI);
    }

}
