package fi.hut.cs.treelib.mdtree;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.LinkPage;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDPage;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.common.AbstractMDPage;
import fi.hut.cs.treelib.common.AbstractMDTree;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.gui.TreeDrawStyle;
import fi.tuska.util.Callback;
import fi.tuska.util.Converter;
import fi.tuska.util.DoubleValueList;
import fi.tuska.util.Pair;
import fi.tuska.util.iterator.Iterables;

/**
 * Page class for ordered multidimensional trees. Used directly by J-tree and
 * Hilbert R-tree.
 * 
 * @author thaapasa
 */
public class OMDPage<K extends Key<K>, V extends PageValue<?>, L extends Key<L>> extends
    AbstractMDPage<K, V, L, OMDPage<K, V, L>> implements MDPage<K, V, L>, Component,
    LinkPage<MBR<K>, V> {

    public static final int PAGE_IDENTIFIER = 0x03d7ba6e;

    /** Setting bit for storage: Is the page a root page? */
    protected static final int BIT_ROOTPAGE = 1 << 16; // Bit 16

    /** The search key is the key of the largest entry in this page. */
    private L searchKey;

    protected final AbstractMDTree<K, V, L, OMDPage<K, V, L>> tree;

    // At leaf pages, we only need to have the page MBRs
    // At index pages, we need to have both MBRs and search keys (coordinates)
    // Therefore, for leaf pages, the coordinate (map key) is MBR.getMin()
    // The coordinates are stored separately only for index pages
    private DoubleValueList<L, MBR<K>, PageValue<?>> contents = new DoubleValueList<L, MBR<K>, PageValue<?>>();

    private final Converter<MBR<K>, L> searchKeyCreator;

    /**
     * @param searchKeyCreator creates the search keys from MBRs. Must return
     * an initial value when called with a null parameter.
     */
    public OMDPage(AbstractMDTree<K, V, L, OMDPage<K, V, L>> tree, PageID id,
        Converter<MBR<K>, L> searchKeyCreator) {
        super(id, tree);
        this.searchKey = searchKeyCreator.convert(null);
        this.searchKeyCreator = searchKeyCreator;
        this.tree = tree;
    }

    @Override
    protected void clearContents() {
        contents.clear();
    }

    @Override
    public int getEntryCount() {
        return contents.size();
    }

    @Override
    protected void attachChild(OMDPage<K, V, L> page) {
        putContents(page.getSearchKey(), page.getPageMBR(), page.getPageID());
    }

    protected Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> contentIterator() {
        return contents.iterator();
    }

    protected Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> descendingContentIterator() {
        return contents.descendingIterator();
    }

    /**
     * Searches for the child page by taking the coordinate of the child page
     * and (Xmin, Xmax); and searches for the floor entry from the contents.
     */
    @Override
    public PageID findChildPointer(MBR<K> key) {
        if (isLeafPage())
            throw new UnsupportedOperationException("Not supported for child pages");

        return (PageID) getFirstCeilingEntry(searchKeyCreator.convert(key)).getSecond()
            .getSecond();
    }

    /**
     * @param mbr the key is taken from the mbr (mbr.getMin())
     * @return the entry with this exact given key (mbr.getMin())
     */
    @Override
    public PageValue<?> getEntry(MBR<K> mbr) {
        return getEntryBySearchKey(searchKeyCreator.convert(mbr));
    }

    @SuppressWarnings("unchecked")
    public Pair<MBR<K>, V> getLastLeafEntry() {
        assert isLeafPage() : this + " is not a leaf page";
        return (Pair<MBR<K>, V>) contents.lastEntry().getSecond();
    }

    public Pair<L, Pair<MBR<K>, PageValue<?>>> getLastEntry() {
        return contents.lastEntry();
    }

    public Pair<L, Pair<MBR<K>, PageValue<?>>> getFirstEntry() {
        return contents.firstEntry();
    }

    @SuppressWarnings("unchecked")
    public Pair<MBR<K>, V> getFirstLeafEntry() {
        assert isLeafPage() : this + " is not a leaf page";
        return (Pair<MBR<K>, V>) contents.firstEntry().getSecond();
    }

    /**
     * @return the entry with this exact given key
     */
    public PageValue<?> getEntryBySearchKey(L key) {
        Pair<MBR<K>, PageValue<?>> e = contents.getFirst(key);
        return e != null ? e.getSecond() : null;
    }

    public Pair<MBR<K>, PageValue<?>> getRouter(L key) {
        assert !isLeafPage();
        Pair<MBR<K>, PageValue<?>> router = contents.getFirst(key);
        assert router != null : "No router found for " + key + " in " + this;
        return router;
    }

    public L getMaxStoredKey() {
        return contents.getLastKey();
    }

    public Pair<L, Pair<MBR<K>, PageValue<?>>> getMiddleEntry() {
        return contents.getAt(contents.size() / 2);
    }

    public Pair<L, Pair<MBR<K>, PageValue<?>>> getEntryAt(int i) {
        return contents.getAt(i);
    }

    /**
     * @return the entry with this exact given key
     */
    public List<Pair<MBR<K>, PageValue<?>>> getEntries(L key) {
        return contents.getAll(key);
    }

    /**
     * This search function will search for the last entry in the contents
     * that comes before (or is the same than) the given key.
     */
    public Pair<L, Pair<MBR<K>, PageValue<?>>> getFirstCeilingEntry(L key) {
        Pair<L, Pair<MBR<K>, PageValue<?>>> val = contents.getFirstCeiling(key);
        return val;
    }

    public PageID getChildRouter(L key) {
        assert !isLeafPage();
        return (PageID) getEntryBySearchKey(key);
    }

    /**
     * Contains means that key is totally inside this MBR (borders may be
     * same).
     */
    @Override
    public boolean contains(MBR<K> key) {
        if (isLeafPage()) {
            // In leaf pages, the entry with a given MBR must have a search
            // key of MBR.getMin()
            Pair<MBR<K>, PageValue<?>> val = contents.getByFirst(searchKeyCreator.convert(key),
                key);
            return val != null;
        } else {
            // Well, this index can contain this MBR if the key MBR is totally
            // inside the index page's MBR
            return getPageMBR().contains(key);
        }
    }

    @Override
    public boolean isRoot(PagePath<MBR<K>, V, ? extends Page<MBR<K>, V>> path) {
        return isRoot();
    }

    /**
     * Return the MBRs.
     */
    @Override
    public List<MBR<K>> getKeys() {
        List<MBR<K>> list = new ArrayList<MBR<K>>();
        for (Pair<L, Pair<MBR<K>, PageValue<?>>> entry : contents) {
            list.add(entry.getSecond().getFirst());
        }
        return list;
    }

    @Override
    public TextLine[] getPageData(int version, TreeDrawStyle scheme) {
        String[] keys = new String[contents.size()];
        int c = 0;
        for (Pair<L, Pair<MBR<K>, PageValue<?>>> entry : contents) {
            StringBuilder b = new StringBuilder();
            b.append(entry.getFirst().toString());
            b.append(" (").append(entry.getSecond().getFirst().toString()).append(")");
            if (!isLeafPage()) {
                b.append(": ").append(((PageID) entry.getSecond().getSecond()).intValue());
            } else {
                b.append(": ").append(entry.getSecond().getSecond());
            }
            keys[c++] = b.toString();
        }
        assert c == contents.size() : c + ", " + contents.size() + ", in " + this;
        return TextLine.toTextLines(keys);
    }

    /** Sets the page dirty. */
    @Override
    public void putContents(KeyRange<MBR<K>> mbr, PageValue<?> value) {
        assert isLeafPage();
        if (!isLeafPage())
            throw new IllegalArgumentException("Can only be used for leaf pages!");
        putContents(mbr.getMin(), value);
    }

    @Override
    public void putContents(MBR<K> mbr, PageValue<?> value) {
        assert isLeafPage();
        if (!isLeafPage())
            throw new IllegalArgumentException("Can only be used for leaf pages!");
        putContents(searchKeyCreator.convert(mbr), mbr, value);
    }

    public void putContents(OMDPage<K, V, L> page) {
        putContents(page.getSearchKey(), page.getPageMBR(), page.getPageID());
    }

    public void putContents(L searchKey, MBR<K> mbr, PageValue<?> value) {
        // No direct links to other pages
        assert !(value instanceof Page<?, ?>);
        assert !isFull() : String.format("Page %s is full when storing contents", getName());
        assert mbr != null;

        if (isLeafPage()) {
            if (searchKey == null)
                searchKey = searchKeyCreator.convert(mbr);
            else
                assert searchKey.equals(searchKeyCreator.convert(mbr)) : searchKey + " != "
                    + searchKeyCreator.convert(mbr) + " (for " + mbr + ")";
        }
        assert searchKey != null;

        contents.add(searchKey, mbr, value);
        extendPageMBR(mbr);
        setDirty(true);
    }

    public MBR<K> getChildRouterMBR(L key, PageID childID) {
        assert isLeafPage();
        return contents.getBySecond(key, childID).getFirst();
    }

    public Pair<MBR<K>, PageValue<?>> removeContents(L searchKey, PageValue<?> value) {
        // No direct links to other pages
        assert !(value instanceof Page<?, ?>);
        assert searchKey != null;

        Pair<MBR<K>, PageValue<?>> removed = contents.removeBySecond(searchKey, value);
        assert removed != null : "For key " + searchKey + " (" + value + ") at page " + this;

        setDirty(true);
        return removed;
    }

    public PageValue<?> removeContents(L searchKey, MBR<K> mbr) {
        // No direct links to other pages
        assert searchKey != null;
        assert mbr != null;

        Pair<MBR<K>, PageValue<?>> removed = contents.removeByFirst(searchKey, mbr);
        assert removed != null : "For key " + searchKey + " (" + mbr + ") at page " + this;
        assert removed.getSecond() != null : "For key " + searchKey + " (" + mbr + ") at page "
            + this;

        setDirty(true);
        return removed.getSecond();
    }

    /**
     * The search key is the key of the largest entry in this page.
     * 
     * @return the search key
     */
    public L getSearchKey() {
        return searchKey;
    }

    public void setSearchKey(L key) {
        this.searchKey = key;
        setDirty(true);
    }

    public void setSearchKeyFromContents() {
        setSearchKey(contents.getLastKey());
    }

    /**
     * Called when moving pages from one child to a sibling.
     */
    protected void updateMBR(OMDPage<K, V, L> child) {
        assert !isLeafPage();
        L searchKey = child.getSearchKey();
        removeContents(searchKey, child.getPageID());
        putContents(searchKey, child.getPageMBR(), child.getPageID());
    }

    protected void enlargeRouterMBR(L key, PageID childId, MBR<K> enlargeMBR) {
        assert !isLeafPage();
        Pair<MBR<K>, PageValue<?>> old = contents.getBySecond(key, childId);
        assert old != null : key + ": " + childId + " at " + this;
        MBR<K> routerMBR = old.getFirst();
        if (!routerMBR.contains(enlargeMBR)) {
            removeContents(key, childId);
            routerMBR = routerMBR.extend(enlargeMBR);
            putContents(key, routerMBR, childId);
        }
    }

    /**
     * Called after redistributing entries (search key has changed). The new
     * search key will be updated to the child page.
     */
    protected void updateSearchKey(OMDPage<K, V, L> child, L newSearchKey) {
        assert !isLeafPage();
        L oldSearchKey = child.getSearchKey();
        // Remove old pointer
        removeContents(oldSearchKey, child.getPageID());
        // Update child search key
        child.setSearchKey(newSearchKey);
        // Add new pointer
        putContents(newSearchKey, child.getPageMBR(), child.getPageID());
    }

    /**
     * Leaf pages need to store their search keys
     */
    @Override
    protected int getCustomHeaderSize() {
        int size = 0;
        // Search key needs to be stored separately
        size += searchKey.getByteDataSize();
        return size;
    }

    @Override
    protected void loadPageCustomHeader(ByteBuffer pageData) {
        // Load search key
        searchKey = searchKey.readFromBytes(pageData);
    }

    @Override
    protected void savePageCustomHeader(ByteBuffer pageData) {
        // Store search key
        searchKey.writeToBytes(pageData);
    }

    @Override
    public int getSingleEntrySize() {
        int size = 0;
        // MBR size
        size += dbConfig.getKeyPrototype().getByteDataSize();
        if (isLeafPage()) {
            // Data storage size
            size += dbConfig.getValuePrototype().getByteDataSize();
        } else {
            // Search key for index pages (coordinate)
            size += getSearchKey().getByteDataSize();
            // Data storage size
            size += PageID.PROTOTYPE.getByteDataSize();
        }
        return size;
    }

    @Override
    public boolean loadPageDataImpl(ByteBuffer pageData) {
        if (!super.loadPageDataImpl(pageData))
            return false;
        if (!isLeafPage()) {
            // Get search key from contents (except for root, which has the
            // default search key)
            if (!contents.isEmpty() && !isRoot()) {
                searchKey = contents.lastEntry().getFirst();
            }
            assert searchKey != null;
        }
        return true;
    }

    @Override
    protected MBR<K> loadSingleEntry(ByteBuffer data, int index, int entryCount) {
        assert data.remaining() >= getSingleEntrySize() : data.remaining() + " < "
            + getSingleEntrySize();
        // Read MBR
        MBR<K> mbr = dbConfig.getKeyPrototype().readFromBytes(data);
        L key = null;

        if (!isLeafPage()) {
            // Read coordinate for index pages
            key = getSearchKey().readFromBytes(data);
        }

        // Read stored value
        PageValue<?> value = null;
        if (isLeafPage())
            value = dbConfig.getValuePrototype().readFromBytes(data);
        else
            value = PageID.PROTOTYPE.readFromBytes(data);

        assert value != null;
        putContents(key, mbr, value);
        return mbr;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object saveSingleEntry(ByteBuffer data, int index, Object position) {
        int dRem = data.remaining();
        assert dRem >= getSingleEntrySize() : dRem + " < " + getSingleEntrySize();

        Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>> lastSaveIterator = (Iterator<Pair<L, Pair<MBR<K>, PageValue<?>>>>) position;
        if (lastSaveIterator == null) {
            lastSaveIterator = contents.iterator();
        }
        if (!lastSaveIterator.hasNext())
            throw new IllegalStateException("Bug: no more elements");
        Pair<L, Pair<MBR<K>, PageValue<?>>> entry = lastSaveIterator.next();

        // Write MBR
        MBR<K> curKey = entry.getSecond().getFirst();
        curKey.writeToBytes(data);

        // For index pages, write search coordinate
        if (!isLeafPage()) {
            entry.getFirst().writeToBytes(data);
        }

        // Write stored contents
        PageValue<?> value = entry.getSecond().getSecond();
        assert value != null;
        int pos = data.position();
        value.writeToBytes(data);
        if (isLeafPage()) {
            assert data.position() == pos + dbConfig.getValuePrototype().getByteDataSize() : "For value: "
                + value;
        } else {
            assert data.position() == pos + PageID.PROTOTYPE.getByteDataSize() : "For value: "
                + value;
        }

        assert data.remaining() == dRem - getSingleEntrySize() : data.remaining() + " != "
            + (dRem - getSingleEntrySize());
        return lastSaveIterator;
    }

    /**
     * Only used for visualization. Does not follow proper page fixing
     * policies (collects unfixed pages).
     */
    protected void collectPagesAtHeight(final int height,
        final Collection<VisualizablePage<MBR<K>, V>> result) {
        if (getHeight() == height) {
            result.add(this);
        } else {
            if (getHeight() > height) {
                assert !isLeafPage();
                processMBREntries(new Callback<Pair<MBR<K>, PageValue<?>>>() {
                    @Override
                    public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                        PageID childID = (PageID) entry.getSecond();
                        OMDPage<K, V, L> child = dbConfig.getPageBuffer().fixPage(childID,
                            factory, false, tree.internalOwner);
                        child.collectPagesAtHeight(height, result);
                        dbConfig.getPageBuffer().unfix(child, tree.internalOwner);
                        return true;
                    }
                });

            }
        }
    }

    /**
     * Convenience method from VisualizablePage. Does not follow proper page
     * fixing policies. Must only be used by the visualization program.
     */
    @Override
    public List<VisualizablePage<MBR<K>, V>> getChildren() {
        final List<VisualizablePage<MBR<K>, V>> res = new ArrayList<VisualizablePage<MBR<K>, V>>();
        if (isLeafPage())
            // Leaves have no childre
            return res;

        processMBREntries(new Callback<Pair<MBR<K>, PageValue<?>>>() {
            @Override
            public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                PageID pageID = (PageID) entry.getSecond();
                OMDPage<K, V, L> bChild = dbConfig.getPageBuffer().fixPage(pageID, factory,
                    false, tree.internalOwner);
                res.add(bChild);
                // The pages are released before the calling program uses
                // them, so this only works for the visualization program.
                dbConfig.getPageBuffer().unfix(bChild, tree.internalOwner);
                return true;
            }
        });

        return res;
    }

    @Override
    public void printDebugInfo() {
        super.printDebugInfo();
        System.out.println("Page search key: " + getSearchKey());
        System.out.println("Page MBR: " + getPageMBR());
        for (Pair<L, Pair<MBR<K>, PageValue<?>>> entry : contents) {
            System.out.println(entry.getFirst() + " (" + entry.getSecond().getFirst() + "): "
                + entry.getSecond().getSecond());
        }
    }

    protected void processMBREntriesFloorKey(Callback<Pair<MBR<K>, PageValue<?>>> callback, L key) {
        for (Pair<MBR<K>, PageValue<?>> entry : contents.getAllFloor(key).getSecond()) {
            callback
                .callback(new Pair<MBR<K>, PageValue<?>>(entry.getFirst(), entry.getSecond()));
        }
    }

    @Override
    protected boolean processMBREntries(Callback<Pair<MBR<K>, PageValue<?>>> callback) {
        for (Pair<L, Pair<MBR<K>, PageValue<?>>> entry : contents) {
            boolean doCont = callback.callback(entry.getSecond());
            if (!doCont)
                return false;
        }
        return true;
    }

    @Override
    public String getName() {
        String name = String.format("(%s:%s, %s)", getSearchKey(), getPageMBR(), getShortName());
        return name;
    }

    @Override
    public List<PageValue<?>> getEntries() {
        List<PageValue<?>> list = new ArrayList<PageValue<?>>(getEntryCount());
        for (Pair<L, Pair<MBR<K>, PageValue<?>>> entry : contents) {
            list.add(entry.getSecond().getSecond());
        }
        return list;
    }

    /**
     * Must only be used by the visualizer program. Will directly fetch the
     * linked node from node buffer (without locking), and will unfix the page
     * so that the visualizer does not need to worry about page fixing.
     */
    @Override
    public List<Page<MBR<K>, V>> getLinks() {
        return new ArrayList<Page<MBR<K>, V>>();
    }

    /**
     * @return the sibling page, fixed to the page buffer. Remember to release
     * it.
     */
    protected OMDPage<K, V, L> getHigherSibling(L searchKey, MBR<K> mbr, PageID pageID,
        Owner owner) {
        assert !isLeafPage();
        Pair<L, Pair<MBR<K>, PageValue<?>>> entry = contents.nextEntry(searchKey,
            new Pair<MBR<K>, PageValue<?>>(mbr, pageID));
        if (entry == null)
            return null;
        PageID router = (PageID) entry.getSecond().getSecond();
        assert !entry.getSecond().getFirst().equals(mbr);
        return dbConfig.getPageBuffer().fixPage(router, factory, false, owner);
    }

    /**
     * @return the sibling page, fixed to the page buffer. Remember to release
     * it.
     */
    protected OMDPage<K, V, L> getLowerSibling(L searchKey, MBR<K> mbr, PageID pageID, Owner owner) {
        assert !isLeafPage();
        Pair<L, Pair<MBR<K>, PageValue<?>>> entry = contents.previousEntry(searchKey,
            new Pair<MBR<K>, PageValue<?>>(mbr, pageID));
        if (entry == null)
            return null;
        PageID router = (PageID) entry.getSecond().getSecond();
        assert !entry.getSecond().getFirst().equals(mbr);
        return dbConfig.getPageBuffer().fixPage(router, factory, false, owner);
    }

    /**
     * Moves the first key to another sibling, so the sibling page's key range
     * will change.
     * 
     * @return the new ending point of the sibling's key range
     */
    protected L moveFirst(OMDPage<K, V, L> toSibling) {
        Pair<L, Pair<MBR<K>, PageValue<?>>> entry = contents.firstEntry();
        L searchKey = entry.getFirst();
        Pair<MBR<K>, PageValue<?>> value = entry.getSecond();
        removeContents(searchKey, value.getSecond());
        toSibling.putContents(searchKey, value.getFirst(), value.getSecond());
        return searchKey;
    }

    /**
     * Moves the last key to another sibling, so this page's key range will
     * change.
     * 
     * @return the new ending point of this page's key range; or null, if this
     * page became empty
     */
    protected L moveLast(OMDPage<K, V, L> toSibling) {
        Pair<L, Pair<MBR<K>, PageValue<?>>> entry = contents.lastEntry();
        L searchKey = entry.getFirst();
        Pair<MBR<K>, PageValue<?>> value = entry.getSecond();
        removeContents(searchKey, value.getSecond());
        toSibling.putContents(searchKey, value.getFirst(), value.getSecond());

        return !contents.isEmpty() ? contents.lastEntry().getFirst() : null;
    }

    /**
     * Dirties this node, the sibling and the parent. This move operation will
     * not change the search key of the sibling node, because only larger keys
     * will be moved to the sibling.
     * 
     * @param toSibling the page to the right of this page
     */
    protected void moveAllEntries(OMDPage<K, V, L> toSibling, OMDPage<K, V, L> parent) {
        setDirty(true);
        toSibling.setDirty(true);
        while (!contents.isEmpty()) {
            moveLast(toSibling);
        }
        if (parent != null) {
            parent.updateMBR(toSibling);
        }
    }

    @Override
    public Iterator<Pair<L, MBR<K>>> getUniqueKeys() {
        return contents.firstIterator();
    }

    @Override
    public MDPage<K, V, L> getUniqueChild(L key, MBR<K> mbr, Owner owner) {
        PageID childID = (PageID) contents.getByFirst(key, mbr).getSecond();
        if (childID == null)
            return null;
        return dbConfig.getPageBuffer().fixPage(childID, factory, false, owner);
    }

    @Override
    public void checkConsistency(Object... params) {
        assert getPageMBR() != null;
        // Check that page MBR is correct (same as the union of the router
        // MBRs)
        MBR<K> calcMBR = null;
        for (Pair<MBR<K>, PageValue<?>> entry : Iterables.get(contents.contentIterator())) {
            MBR<K> childMBR = entry.getFirst();
            calcMBR = calcMBR != null ? calcMBR.extend(childMBR) : childMBR;
        }
        assert calcMBR != null;
        assert calcMBR.equals(getPageMBR()) : calcMBR + " != " + getPageMBR() + " at " + this;
        // Check that page search key is correct (>= the last entry key)
        // searchKey >= contents.lastKey
        assert getSearchKey().compareTo(contents.getLastKey()) >= 0 : getSearchKey() + " < "
            + contents.getLastKey() + " at " + this;

        // Test min entry count
        if (!isRoot()) {
            assert contents.size() >= getMinEntries() : contents.size() + " < " + getMinEntries()
                + " entries at " + this;
        }

        // Check that router MBRs match child page MBRs, and router search
        // keys match child page search keys, recurse down to child pages
        for (Pair<L, Pair<MBR<K>, PageValue<?>>> entry : contents) {
            MBR<K> cMBR = entry.getSecond().getFirst();
            assert getPageMBR().contains(cMBR);
            if (!isLeafPage()) {
                PageID cID = (PageID) entry.getSecond().getSecond();
                assert cID != null;
                assert cID.isValid();
                L cSKey = entry.getFirst();
                assert cSKey != null;
                OMDPage<K, V, L> child = dbConfig.getPageBuffer().fixPage(cID, factory, false,
                    tree.internalOwner);
                assert child != null;
                assert child.getSearchKey().equals(cSKey) : this + ":" + cSKey + " != "
                    + child.getSearchKey() + "@" + child;
                assert child.getPageMBR().equals(cMBR) : this + ":" + cMBR + " != "
                    + child.getPageMBR() + "@" + child;
                child.checkConsistency();
                dbConfig.getPageBuffer().unfix(child, tree.internalOwner);
            }
        }
    }

    @Override
    public boolean containsChild(PageID childID) {
        assert !isLeafPage();
        for (Pair<L, Pair<MBR<K>, PageValue<?>>> entry : contents) {
            if (childID.equals(entry.getSecond().getSecond()))
                return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public Pair<MBR<K>, V> findFloorEntry(MBR<K> key, L searchKey, boolean defaultToFirstEntry,
        Owner owner) {
        Pair<L, Pair<MBR<K>, PageValue<?>>> entry = contents.getLastFloor(searchKey);
        if (entry == null) {
            if (!defaultToFirstEntry)
                return null;
            entry = contents.firstEntry();
        }
        assert entry != null;

        if (isLeafPage()) {
            return new Pair<MBR<K>, V>(entry.getSecond().getFirst(), (V) entry.getSecond()
                .getSecond());
        } else {
            PageID childID = (PageID) entry.getSecond().getSecond();
            OMDPage<K, V, L> child = dbConfig.getPageBuffer().fixPage(childID, factory, false,
                owner);
            assert child != null;
            Pair<MBR<K>, V> val = child
                .findFloorEntry(key, searchKey, defaultToFirstEntry, owner);
            dbConfig.getPageBuffer().unfix(child, owner);
            return val;
        }
    }

    @SuppressWarnings("unchecked")
    public Pair<MBR<K>, V> findNextEntry(MBR<K> mbr, L searchKey, Owner owner) {
        if (isLeafPage()) {
            // Find the entry from this page
            for (Pair<L, Pair<MBR<K>, PageValue<?>>> entry : contents) {
                L curKey = entry.getFirst();
                if (curKey.compareTo(searchKey) > 0) {
                    // Found the first entry that is larger than the searched
                    // L-key
                    return (Pair<MBR<K>, V>) entry.getSecond();
                }
            }
            // No larger entry found from this page
            return null;
        } else {
            boolean startBrowsing = false;
            // Find the entry from this page
            for (Pair<L, Pair<MBR<K>, PageValue<?>>> entry : contents) {
                // curKey is the largest entry in the given page
                L curKey = entry.getFirst();
                // Start browsing at the first entry that is larger
                if (!startBrowsing && curKey.compareTo(searchKey) >= 0)
                    startBrowsing = true;
                if (startBrowsing) {
                    PageID childID = (PageID) entry.getSecond().getSecond();
                    OMDPage<K, V, L> child = dbConfig.getPageBuffer().fixPage(childID, factory,
                        false, owner);
                    assert child != null;
                    Pair<MBR<K>, V> val = child.findNextEntry(mbr, searchKey, owner);
                    dbConfig.getPageBuffer().unfix(child, owner);
                    if (val != null)
                        // Found the next entry
                        return val;
                }
            }
            // No next entry found
            return null;
        }
    }

    @Override
    public void format(int height) {
        super.format(height);
        // Set default search key
        setSearchKey(searchKeyCreator.convert(null));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected V removeEntry(MBR<K> key) {
        return (V) contents.removeByFirst(searchKeyCreator.convert(key), key);
    }

    @Override
    public int getTypeIdentifier() {
        return PAGE_IDENTIFIER;
    }
}
