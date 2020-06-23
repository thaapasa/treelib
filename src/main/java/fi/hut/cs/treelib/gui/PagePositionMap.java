package fi.hut.cs.treelib.gui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.VisualizablePage;

public class PagePositionMap {

    private Map<PageID, PositionInfo> positions = new HashMap<PageID, PositionInfo>();
    private Map<Integer, Integer> levelOffset = new HashMap<Integer, Integer>();
    private Map<Integer, List<PositionInfo>> levelPositions = new HashMap<Integer, List<PositionInfo>>();

    private final boolean simplePageCentering;
    private final int horizontalSpacing;
    private final int verticalSpacing;
    private final int version;
    private final int fontHeight;
    private final Dimension defaultPageSize;
    private final int maxLevels;
    private int pageHeight;
    private int maxWidth;
    private TreeDrawStyle scheme;
    private FontMetrics metrics;

    public PagePositionMap(int maxLevels, int version, int horizontalSpacing,
        int verticalSpacing, int fontHeight, Dimension defaultPageSize, TreeDrawStyle scheme,
        FontMetrics metrics) {
        this.horizontalSpacing = horizontalSpacing;
        this.verticalSpacing = verticalSpacing;
        this.scheme = scheme;
        this.version = version;
        this.fontHeight = fontHeight;
        this.defaultPageSize = defaultPageSize;
        assert maxLevels >= 0 : maxLevels;
        this.maxLevels = maxLevels;
        this.metrics = metrics;
        this.simplePageCentering = Configuration.instance().isSimplePageCentering();
        assert metrics != null;
    }

    public boolean addTree(Tree<?, ?, ?> tree) {
        Page<?, ?> root = tree.getRoot(GUIElements.VISUALIZER_OWNER);
        if (root == null)
            return false;

        VisualizablePage<?, ?> vRoot = (VisualizablePage<?, ?>) root;
        addPageRecursive(vRoot);
        if (tree.getPageBuffer() != null) {
            tree.getPageBuffer().unfix(root, GUIElements.VISUALIZER_OWNER);
        }
        return true;
    }

    protected void addPageRecursive(VisualizablePage<?, ?> page) {
        for (VisualizablePage<?, ?> child : page.getChildren()) {
            // Add child pages if they have not yet been added
            if (!positions.containsKey(child.getPageID())) {
                addPageRecursive(child);
            }
        }
        addPage(page);
    }

    public void addPage(Page<?, ?> page) {
        // Check if this page has already been added to the position map
        PositionInfo posInfo = positions.get(page.getPageID());
        if (posInfo != null) {
            return;
        }
        // Find original X offset for page
        int level = page.getHeight();
        int orgOffset = getLevelWidth(level);

        // Create new position info entry
        posInfo = new PositionInfo(page, level, orgOffset);
        positions.put(page.getPageID(), posInfo);

        // Update total width for the given level
        int newOffset = orgOffset + horizontalSpacing + posInfo.getWidth();
        levelOffset.put(level, newOffset);
        if (newOffset > maxWidth) {
            maxWidth = newOffset;
        }

        // Add page to per-level page list
        List<PositionInfo> positions = levelPositions.get(level);
        if (positions == null) {
            positions = new ArrayList<PositionInfo>();
            levelPositions.put(level, positions);
        }
        positions.add(posInfo);
    }

    protected int getLevelWidth(int level) {
        Integer width = levelOffset.get(level);
        return width != null ? width.intValue() : 0;
    }

    public Dimension getSize() {
        assert maxLevels > 0 : maxLevels;
        assert maxWidth > 0 : maxWidth;
        return new Dimension(maxWidth - horizontalSpacing + 1, maxLevels
            * (verticalSpacing + pageHeight) - verticalSpacing + 1);
    }

    public PositionInfo getPosition(Page<?, ?> page) {
        return positions.get(page.getPageID());
    }

    public boolean containsPage(Page<?, ?> page) {
        return positions.containsKey(page.getPageID());
    }

    public void centerPages() {
        for (Entry<Integer, List<PositionInfo>> entry : levelPositions.entrySet()) {
            int level = entry.getKey();
            int width = getLevelWidth(level);
            if (width < maxWidth) {
                // Redistribute
                centerPagesAtLevel(entry.getValue(), maxWidth);
                // Set level width to the maximum
                levelOffset.put(level, maxWidth);
            }
        }
    }

    protected void centerPagesAtLevel(List<PositionInfo> levelPositions, int totalWidth) {
        int pageCount = levelPositions.size();
        assert levelPositions.size() > 0;
        if (simplePageCentering || levelPositions.get(0).getPage().isLeafPage()) {
            int itemWidth = totalWidth / pageCount;
            int offsToCenter = itemWidth / 2;
            int offsFromLeft = 0;
            for (PositionInfo pos : levelPositions) {
                pos.setX(offsFromLeft + offsToCenter - pos.getWidth() / 2);
                offsFromLeft += itemWidth;
            }
        } else {
            // Calculate page position based on child page positions
            for (PositionInfo pos : levelPositions) {
                Page<?, ?> page = pos.getPage();

                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                for (PageValue<?> val : page.getEntries()) {
                    if (val instanceof PageID) {
                        PageID pageID = (PageID) val;
                        PositionInfo cPos = positions.get(pageID);
                        minX = Math.min(minX, cPos.getMinX());
                        maxX = Math.max(maxX, cPos.getMaxX());
                    }
                }
                int cx = (maxX - minX) / 2 + minX - pos.getWidth() / 2;
                pos.setX(cx);
            }
        }
    }

    public class PositionInfo {

        private final Page<?, ?> page;
        private final Dimension pageSize;
        private final int level;
        private Point2D position;
        private int x;
        private boolean drawn;

        public PositionInfo(Page<?, ?> page, int level, int xOffset) {
            this.page = page;
            this.pageSize = (page instanceof VisualPage) ? ((VisualPage) page).getPageDrawSize(
                version, fontHeight, scheme, metrics) : defaultPageSize;
            this.x = xOffset;
            this.level = level;
            this.drawn = false;
            if (pageSize.getHeight() > pageHeight) {
                pageHeight = (int) pageSize.getHeight();
            }
        }

        public Page<?, ?> getPage() {
            return page;
        }

        public boolean isDrawn() {
            return drawn;
        }

        public void setDrawn() {
            this.drawn = true;
        }

        public int getMinX() {
            return x;
        }

        public int getMaxX() {
            return getMinX() + getWidth();
        }

        public int getMinY() {
            return (pageHeight + verticalSpacing) * (maxLevels - level);
        }

        public int getMaxY() {
            return getMinY() + (int) pageSize.getHeight();
        }

        protected void setX(int x) {
            this.x = x;
        }

        public int getWidth() {
            return (int) pageSize.getWidth();
        }

        public Dimension getPageSize() {
            return pageSize;
        }

        public Point2D getPosition() {
            if (position == null) {
                position = new Point(x, getMinY());
            }
            return position;
        }

        public Point2D getParentLinkStartPos() {
            Dimension pageSize = getPageSize();
            int x = (int) (getPosition().getX() + pageSize.getWidth() / 2.0);
            int y = (int) (getPosition().getY() + pageSize.getHeight());
            return new Point(x, y);
        }

        public Point2D getParentLinkEndPos() {
            Dimension pageSize = getPageSize();
            int x = (int) (getPosition().getX() + pageSize.getWidth() / 2.0);
            return new Point(x, (int) position.getY());
        }

        public Point2D getPeerLinkStartPos() {
            Dimension pageSize = getPageSize();
            int x = (int) (getPosition().getX() + pageSize.getWidth()) + 1;
            int y = (int) (getPosition().getY() + pageSize.getHeight() / 2.0);
            return new Point(x, y);
        }

        public Point2D getPeerLinkEndPos() {
            Dimension pageSize = getPageSize();
            int y = (int) (getPosition().getY() + pageSize.getHeight() / 2.0);
            return new Point((int) getPosition().getX(), y);
        }

        @Override
        public String toString() {
            return String.format("Page %s at %.0fx%.0f, w: %d", page.getName(), getPosition()
                .getX(), getPosition().getY(), getWidth());
        }

    }
}
