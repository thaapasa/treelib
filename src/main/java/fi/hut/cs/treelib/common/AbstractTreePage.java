package fi.hut.cs.treelib.common;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.gui.TreeDrawStyle;
import fi.hut.cs.treelib.gui.VisualPage;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.storage.AbstractPage;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

/**
 * Abstract base class for B-trees and multiversion B-Trees (MVBT). Contains
 * the most basic functionality (height, page id, type (leaf/index page),
 * etc.).
 * 
 * @author thaapasa
 * 
 * @param <K> database key type (e.g., IntegerKey)
 * @param <V> database value type (e.g., StringValue)
 */
public abstract class AbstractTreePage<K extends Key<K>, V extends PageValue<?>, P extends AbstractTreePage<K, V, P>>
    extends AbstractPage implements Comparable<AbstractTreePage<K, V, P>>, VisualPage,
    Page<K, V>, VisualizablePage<K, V>, Component {

    protected static final String PREFIX_PAGE = "p";
    protected static final String PREFIX_INDEX = "I";
    protected static final String PREFIX_LEAF = "L";

    /** Setting bit for storage: Is the node a root node? */
    protected static final int BIT_ROOTNODE = 1; // Bit 1

    private static final Logger log = Logger.getLogger(AbstractTreePage.class);
    private static boolean SHOW_SUMMARY = false;

    /** Amount of entries that fit to this page. */
    private int pageCapacity;

    private int height;
    private boolean isRoot;

    /**
     * Controls whether the page names should contain information about the
     * page type (Ixx or Lxx), or just be named similarly (Pxx and Pxx).
     */
    private final boolean typedPageNames;

    /**
     * Range of keys in this page. Invariant: child keyRange is always the
     * same as the keyRange used as key in the parent IndexPage to point to
     * this child.
     * 
     * if isKeyRangeUsed() returns false, then this is not used and not stored
     * to disk (multidimensional trees, for example).
     */
    private KeyRange<K> keyRange;

    protected final DatabaseConfiguration<K, V> dbConfig;

    /** Page factory for creating new pages to the tree. */
    protected final PageFactory<P> factory;

    public AbstractTreePage(PageID id, AbstractTree<K, V, P> tree, int pageSize) {
        super(id, pageSize);
        this.dbConfig = tree.getDBConfig();
        this.height = 1;
        this.factory = tree.getPageFactory();
        assert factory != null;

        this.typedPageNames = Configuration.instance().isTypedPageNames();
        this.keyRange = getDefaultKeyRange();
    }

    protected void calculateCapacity() {
        this.pageCapacity = (getPageSize() - getPageHeaderSize()) / getSingleEntrySize();
        Configuration c = Configuration.instance();
        if (c.isLimitPageSizes()) {
            int limit = isLeafPage() ? c.getLeafPageSizeLimit() : c.getIndexPageSizeLimit();
            if (this.pageCapacity > limit)
                this.pageCapacity = limit;
        }
    }

    protected void attachChild(P page) {
        putContents(page.getKeyRange(), page.getPageID());
    }

    public double getFillRatio() {
        return (double) getEntryCount() / (double) getPageEntryCapacity();
    }

    /**
     * Override to return false if the implementation does not use key range.
     * Then it will not be stored to disk either.
     */
    protected boolean isKeyRangeUsed() {
        return true;
    }

    @Override
    public void formatNewPage(ByteBuffer pageData) {
        super.formatNewPage(pageData);
        // Formats the page for usage.
        clear();
        calculateCapacity();
    }

    @Override
    public void format(int height) {
        this.height = height;
        if (isKeyRangeUsed()) {
            // Recalculate default key range
            setKeyRange(getDefaultKeyRange());
        }
        calculateCapacity();
    }

    public KeyRange<K> getDefaultKeyRange() {
        return new KeyRangeImpl<K>(dbConfig.getKeyPrototype().getMinKey(), dbConfig
            .getKeyPrototype().getMaxKey());
    }

    @Override
    public KeyRange<K> getKeyRange() {
        return keyRange;
    }

    public void setKeyRange(KeyRange<K> keyRange) {
        this.keyRange = keyRange;
        setDirty(true);
    }

    protected abstract int getMinEntries();

    /**
     * @return the height of the page. Leaves are at height 1, root is always
     * at the most highest point.
     */
    @Override
    public final int getHeight() {
        return height;
    }

    /**
     * @return true if this is a leaf page, false if this is an index page.
     */
    @Override
    public final boolean isLeafPage() {
        return height == 1;
    }

    /**
     * @return a short informative name for this page, to be used for
     * identifying the page in visualizations or logging.
     */
    @Override
    public String getShortName() {
        return String.format("%s%d", typedPageNames ? (isLeafPage() ? PREFIX_LEAF : PREFIX_INDEX)
            : PREFIX_PAGE, getPageID().intValue());
    }

    /**
     * @return the amount of physical entries that can fit in this page. In
     * index pages, this means the amount of child pointers. In leaf pages,
     * this means the amount of data items.
     */
    @Override
    public final int getPageEntryCapacity() {
        assert pageCapacity > 0 : "Capacity not calculated";
        return pageCapacity;
    }

    /**
     * @return the amount of entries that still fit to this page, from 0 to
     * pageCapacity.
     */
    public final int getFreeEntries() {
        return getPageEntryCapacity() - getEntryCount();
    }

    @Override
    public final boolean isFull() {
        assert pageCapacity > 0 : "Capacity not calculated";

        int entries = getEntryCount();
        assert entries <= pageCapacity : String.format(
            "Page %s contains too many entries (%d > %d)", getName(), entries, pageCapacity);

        return entries >= pageCapacity;
    }

    /**
     * @return the child page that contains the given key, fixed to the page
     * buffer.
     */
    @Override
    public P getChild(K key, Owner owner) {
        assert !isLeafPage();
        PageID childID = findChildPointer(key);
        if (childID == null)
            return null;
        P child = dbConfig.getPageBuffer().fixPage(childID, factory, false, owner);
        return child;
    }

    @Override
    public String toString() {
        return String.format("%s, height: %d, entries: %d/%d (%.1f %%), (%sleaf)", getName(),
            getHeight(), getEntryCount(), getPageEntryCapacity(), getFillRatio() * 100,
            isLeafPage() ? "" : "not ");
    }

    @Override
    public String getName() {
        String name = isKeyRangeUsed() ? String.format("(%s, %s)", getKeyRange(), getShortName())
            : getShortName();
        return name;
    }

    @Override
    public TextLine[] getPageName(int version, TreeDrawStyle scheme) {
        return new TextLine[] { new TextLine(getShortName()),
            new TextLine(getKeyRange().toString()) };
    }

    @Override
    public TextLine[] getPageText(int version, TreeDrawStyle scheme) {
        if (!SHOW_SUMMARY) {
            return getPageData(version, scheme);
        }
        String summary = getPageSummary(version);
        TextLine[] data = getPageData(version, scheme);
        TextLine[] res = new TextLine[1 + data.length];
        res[0] = new TextLine(summary);
        System.arraycopy(data, 0, res, 1, data.length);
        return res;
    }

    /**
     * VisualPage: return page size for rendering.
     */
    @Override
    public Dimension getPageDrawSize(int version, int fontHeight, TreeDrawStyle scheme,
        FontMetrics metrics) {
        TextLine[] pageHeader = getPageName(version, scheme);
        TextLine[] pageText = getPageText(version, scheme);
        int max = countMaxLineWidth(pageText, metrics);
        max = Math.max(max, countMaxLineWidth(getPageName(version, scheme), metrics));
        return new Dimension(10 + max, (pageHeader.length + getPageEntryCapacity()) * fontHeight
            + 10);
    }

    protected int countMaxLineWidth(TextLine[] lines, FontMetrics metrics) {
        int max = 0;
        for (TextLine line : lines) {
            int width = metrics.stringWidth(line.text);
            if (width > max) {
                max = width;
            }
        }
        return max;
    }

    @Override
    public Color getPageColor(int version, TreeDrawStyle scheme) {
        // Return null to use the default color
        return null;
    }

    @Override
    public Color getPageParentLinkColor(int version, TreeDrawStyle scheme) {
        // Return null to use the default color
        return null;
    }

    protected String getPageSummary(int version) {
        return String.format("e: %d, h: %d", getEntryCount(), getHeight());
    }

    /**
     * Comparison to other pages for sorting.
     */
    @Override
    public int compareTo(AbstractTreePage<K, V, P> o) {
        if (o == null) {
            if (log.isDebugEnabled())
                log.debug("Other page is null");
            return 1;
        }
        // Cannot compare if key range is not used
        if (!isKeyRangeUsed())
            return -1;
        // KeyRange must not be null
        return getKeyRange().compareTo(o.getKeyRange());
    }

    public abstract TextLine[] getPageData(int version, TreeDrawStyle scheme);

    private void setSettingsBits(int settingBits) {
        setRoot((settingBits & BIT_ROOTNODE) != 0);
        setCustomSettingBits(settingBits);
    }

    private int getSettingsBits() {
        // No settings now for the abstract class
        int bits = 0;
        // Store root bit
        bits |= isRoot() ? BIT_ROOTNODE : 0;

        // Get custom settings bits
        bits |= (getCustomSettingBits() & 0xffff0000);
        return bits;
    }

    /** Bits 0-15 are reserved, 16-31 can be used by extensions */
    protected abstract void setCustomSettingBits(int settingBits);

    /** Bits 0-15 are reserved, 16-31 can be used by extensions */
    protected abstract int getCustomSettingBits();

    protected abstract KeyRange<?> loadSingleEntry(ByteBuffer data, int index, int entryCount);

    /**
     * Stores the entry with the given index to the storage. Guaranteed to be
     * called in order 0..n-1, so that state can be maintained by initializing
     * position when index==0, and it will hold that all consecutive calls
     * until a new index==0 will be done in order.
     * 
     * @return position marker, that will be fed back to the next
     * saveSingleEntry call. Can be null.
     */
    protected abstract Object saveSingleEntry(ByteBuffer data, int index, Object position);

    protected abstract void clearContents();

    protected void clear() {
        setDirty(false);
        height = 1;
        if (isKeyRangeUsed()) {
            setKeyRange(getDefaultKeyRange());
        }
    }

    /**
     * @return page header size, in bytes, including the custom header part
     */
    public int getPageHeaderSize() {
        int size = 0;
        // 4: Page type
        size += 4;
        // 4: Settings (bit field)
        size += 4;
        // 4: Height (integer)
        size += 4;
        // 4: Entry count (integer)
        size += 4;
        // Key range
        if (isKeyRangeUsed()) {
            size += keyRange.getByteDataSize();
        }
        // Custom header
        size += getCustomHeaderSize();
        return size;
    }

    protected int getCustomHeaderSize() {
        return 0;
    }

    protected void loadPageCustomHeader(ByteBuffer pageData) {
    }

    @Override
    public boolean loadPageDataImpl(ByteBuffer pageData) {
        clear();
        pageData.rewind();
        int dataPos = 0;
        // Step 1. Header
        // 1.1: Page type
        final int pageType = pageData.getInt();
        if (pageType == 0) {
            // New page, uninitialized data
            return true;
        }
        if (pageType != getTypeIdentifier()) {
            throw new IllegalArgumentException("Wrong page type: "
                + Integer.toHexString(pageType) + ", expected: "
                + Integer.toHexString(getTypeIdentifier()));
        }
        // 1.2: Settings (bit field)
        final int settingBits = pageData.getInt();
        // 1.3: Height (integer)
        this.height = pageData.getInt();
        // Format the page still
        format(height);
        setSettingsBits(settingBits);

        // 1.4: Entry count (integer)
        final int entryCount = pageData.getInt();

        // 1.5: Key range
        if (isKeyRangeUsed()) {
            keyRange = keyRange.readFromBytes(pageData);
        }

        // Calculate capacity (needs height)
        calculateCapacity();

        // Step 2. Custom page data
        loadPageCustomHeader(pageData);

        // Check and adjust position
        dataPos += getPageHeaderSize();
        assert pageData.position() <= dataPos;
        pageData.position(dataPos);

        if (log.isDebugEnabled())
            log
                .debug(String.format(
                    "Loading page %s from disk, capacity %d * %d + %d (< %d bytes)", getName(),
                    getPageEntryCapacity(), getSingleEntrySize(), getPageHeaderSize(),
                    getPageSize()));

        int entrySize = getSingleEntrySize();
        // Step 3. Entries
        for (int i = 0; i < entryCount; i++) {
            loadSingleEntry(pageData, i, entryCount);

            // Manually set the correct position, if the entry sizes vary
            dataPos += entrySize;
            assert pageData.position() <= dataPos;
            pageData.position(dataPos);
        }
        return true;
    }

    protected void savePageCustomHeader(ByteBuffer pageData) {
    }

    @Override
    public void savePageDataImpl(ByteBuffer pageData) {
        assert pageData.position() == 0;
        int dataPos = 0;

        // Step 1. Header
        // 1.1: Page type
        pageData.putInt(getTypeIdentifier());
        assert pageData.position() == 4;

        // 1.2: Settings (bit field)
        final int settingBits = getSettingsBits();
        pageData.putInt(settingBits);
        assert pageData.position() == 8;

        // 1.3: Height (integer)
        pageData.putInt(this.height);
        assert pageData.position() == 12;

        // 1.4: Entry count (integer)
        final int entryCount = getEntryCount();
        pageData.putInt(entryCount);
        assert pageData.position() == 16;

        // 1.5: Key range
        if (isKeyRangeUsed()) {
            keyRange.writeToBytes(pageData);
        }

        // Step 2. Custom page data
        savePageCustomHeader(pageData);
        dataPos += getPageHeaderSize();
        assert pageData.position() <= dataPos;
        pageData.position(dataPos);

        if (log.isDebugEnabled())
            log.debug(String.format("Saving page %s to disk, capacity %d * %d + %d (< %d bytes)",
                getName(), getPageEntryCapacity(), getSingleEntrySize(), getPageHeaderSize(),
                getPageSize()));

        // Step 2. Entries
        int entrySize = getSingleEntrySize();
        Object position = null;
        for (int i = 0; i < entryCount; i++) {
            position = saveSingleEntry(pageData, i, position);
            // Manually set the correct position, if the entry sizes vary
            dataPos += entrySize;
            assert pageData.position() <= dataPos : pageData.position() + " > " + dataPos
                + " (entry size: " + entrySize + ")";
            pageData.position(dataPos);
        }
    }

    public final void setRoot(boolean state) {
        this.isRoot = state;
        setDirty(true);
    }

    public final boolean isRoot() {
        return isRoot;
    }

    protected abstract void putContents(KeyRange<K> range, PageValue<?> value);

    protected void putContents(K key, PageValue<?> value) {
        assert isLeafPage();
        putContents(KeyRangeImpl.getKeyRange(key), value);
    }

    protected abstract V removeEntry(K key);

    public void traversePages(final Predicate<KeyRange<K>> predicate,
        final Callback<Page<K, V>> operation, Owner owner) {

        final Deque<PageID> pages = new LinkedList<PageID>();
        pages.addLast(getPageID());

        while (!pages.isEmpty()) {
            PageID pageID = pages.removeFirst();
            // Only one page is now fixed at a time
            final P page = dbConfig.getPageBuffer().fixPage(pageID, factory, false, owner);

            final int pageHeight = page.getHeight();
            final KeyRange<K> pageRange = page.getKeyRange();

            if (predicate.matches(pageRange, pageHeight)) {
                if (!operation.callback(page)) {
                    dbConfig.getPageBuffer().unfix(page, owner);
                    return;
                }
            }

            if (pageHeight > 1 && predicate.continueTraversal(pageRange, pageHeight)) {
                final int childHeight = pageHeight - 1;

                // Loop through page's entries
                page.processEntries(new Callback<Pair<KeyRange<K>, PageValue<?>>>() {
                    @Override
                    public boolean callback(Pair<KeyRange<K>, PageValue<?>> entry) {
                        if (predicate.continueTraversal(entry.getFirst(), childHeight)) {
                            PageID childID = (PageID) entry.getSecond();
                            pages.addLast(childID);
                        }
                        return true;
                    }
                });
            }

            dbConfig.getPageBuffer().unfix(page, owner);
        }
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
        SMOPolicy p = dbConfig.getSMOPolicy();
        System.out.println(String.format("Allowed entries: %d - %d / %d", p.getMinEntries(this),
            p.getMaxEntries(this), getPageEntryCapacity()));
        System.out.println(String.format("Allowed entries after SMO: %d - %d", p
            .getMinEntriesAfterSMO(this), p.getMaxEntriesAfterSMO(this)));
    }

}
