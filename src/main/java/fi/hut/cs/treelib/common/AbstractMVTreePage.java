package fi.hut.cs.treelib.common;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVPage;
import fi.hut.cs.treelib.MVTreeOperations;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.gui.TreeDrawStyle;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Pair;
import fi.tuska.util.Triple;
import fi.tuska.util.iterator.Iterables;

public abstract class AbstractMVTreePage<K extends Key<K>, V extends PageValue<?>, P extends AbstractMVTreePage<K, V, P>>
    extends AbstractTreePage<K, V, P> implements MVPage<K, V, P>, VisualizablePage<K, V>,
    Page<K, V> {

    private static final Logger log = Logger.getLogger(AbstractMVTreePage.class);

    /** Pointer to the tree. */
    protected final AbstractMVTree<K, V, P> tree;

    protected TreeMap<MVKeyRange<K>, PageValue<?>> indexContents;

    // Tracks entries when storing keys to page
    private MVKeyRange<K> lastSavedKey;

    protected AbstractMVTreePage(AbstractMVTree<K, V, P> tree, PageID id, int pageSize,
        K keyPrototype, V valuePrototype) {
        super(id, tree, pageSize);

        this.tree = tree;

        // this.contents = new TreeMap<MVKeyRange<K>, PageValue<?>>();
    }

    protected abstract LeafEntryMap<K, V> getLeafEntries();

    @Override
    public int getLiveEntryCount(int version) {
        int count = 0;
        if (isLeafPage()) {
            for (Triple<MVKeyRange<K>, Boolean, V> entry : Iterables.get(getLeafEntries()
                .logicalIterator())) {
                if (entry.getFirst().containsVersion(version)) {
                    count++;
                }
            }
        } else {
            for (MVKeyRange<K> range : indexContents.keySet()) {
                if (range.containsVersion(version)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public int getLatestVersion() {
        return tree.getLatestVersion();
    }

    @Override
    public V removeEntry(K key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRoot(PagePath<K, V, ? extends Page<K, V>> path) {
        assert path.getCurrent() == this;
        return path.getParent() == null;
    }

    @Override
    public MVKeyRange<K> getDefaultKeyRange() {
        return new MVKeyRange<K>(dbConfig.getKeyPrototype().getMinKey(), dbConfig
            .getKeyPrototype().getMaxKey(), tree != null ? tree.getLatestVersion() : 0);
    }

    @Override
    public void setKeyRange(KeyRange<K> range) {
        if (!(range instanceof MVKeyRange<?>))
            throw new IllegalArgumentException(
                "MV-Tree page keyrange must be a multiversion key range");
        super.setKeyRange(range);
        setDirty(true);
    }

    public MVKeyRange<K> findContentKey(K key, int version) {
        assert !isLeafPage();
        for (Entry<MVKeyRange<K>, PageValue<?>> entry : indexContents.entrySet()) {
            MVKeyRange<K> range = entry.getKey();
            if (range.contains(key, version)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public MVKeyRange<K> findContentKey(KeyRange<K> range, int version) {
        assert !isLeafPage();
        KeyRange<K> searchRange = getKeyRange().intersection(range);
        MVKeyRange<K> res = findContentKey(searchRange.getMin(), version);
        if (res == null)
            if (log.isDebugEnabled())
                log.debug("No result found with " + searchRange + ", intersected from "
                    + getKeyRange() + " and " + range);
        return res;
    }

    protected MVTreeOperations<K, V, P> getOperations() {
        return tree.getOperations();
    }

    protected StatisticsLogger getStatisticsLogger() {
        return tree.getStatisticsLogger();
    }

    /**
     * Creates a sibling page of the same type.
     * 
     * @return the newly created, unattached page.
     */
    public P createSibling(Owner owner) {
        P sibling = tree.getPageBuffer().createPage(tree.getPageFactory(), owner);
        // Format page to start from this moment
        sibling.format(getHeight());
        sibling.setKeyRange(new MVKeyRange<K>(getKeyRange().getMin(), getKeyRange().getMax(),
            tree.getLatestVersion()));
        return sibling;
    }

    @Override
    public void format(int height) {
        super.format(height);
        assert this.indexContents == null;
        if (!isLeafPage()) {
            this.indexContents = new TreeMap<MVKeyRange<K>, PageValue<?>>();
        }
    }

    public TreeMap<MVKeyRange<K>, PageValue<?>> getContents() {
        assert !isLeafPage();
        return indexContents;
    }

    /**
     * @return the number of entries currently in this page
     */
    @Override
    public int getEntryCount() {
        if (isLeafPage()) {
            LeafEntryMap<K, V> map = getLeafEntries();
            assert map != null;
            return map.size();
        } else {
            assert indexContents != null;
            return indexContents.size();
        }
    }

    @Override
    public MVKeyRange<K> getKeyRange() {
        return (MVKeyRange<K>) super.getKeyRange();
    }

    public boolean isAlive(int version) {
        return getKeyRange().containsVersion(version);
    }

    public boolean isAlive(KeyRange<IntegerKey> versionRange) {
        return getKeyRange().getVersionRange().overlaps(versionRange);
    }

    public boolean isAlive() {
        return getKeyRange().containsVersion(tree.getLatestVersion());
    }

    @Override
    public Color getPageColor(int version, TreeDrawStyle scheme) {
        return isAlive(version) ? scheme.getPageBGColor() : scheme.getDeadPageBgColor();
    }

    @Override
    public Color getPageParentLinkColor(int version, TreeDrawStyle scheme) {
        return isAlive(version) ? scheme.getPageParentLinkColor() : scheme
            .getDeadPageParentLinkColor();
    }

    @Override
    protected String getPageSummary(int version) {
        return String.format("e: %d, h: %d", getEntryCount(), getHeight());
    }

    @Override
    public void insertRouter(P child, PagePath<K, V, P> path, Transaction<K, V> tx) {
        assert !isLeafPage();

        if (isFull()) {
            assert path != null;
            assert path.getCurrent() == this;
            getOperations().split(path, child.getKeyRange().getMin(), null, false, tx);
            P newParent = path.getCurrent();

            if (log.isDebugEnabled())
                log.debug(String.format("Created new parent from %s to hold "
                    + "inserted entry, new parent is %s", getName(), newParent.getName()));
            assert !newParent.isFull() : String.format("Parent %s is full when inserting a "
                + "router entry", newParent.getName());
            newParent.insertRouter(child, path, tx);
            return;
        }

        // putContents sets this page dirty
        putContents(child.getKeyRange(), child.getPageID());
    }

    /**
     * Attaches a child page into the children-map.
     */
    @Override
    public void putContents(KeyRange<K> range, PageValue<?> value) {
        if (!(range instanceof MVKeyRange<?>))
            throw new IllegalArgumentException("Range not multiversion: " + range);
        assert !isFull() : String.format("Page %s is full when storing contents", getName());
        indexContents.put((MVKeyRange<K>) range, value);
        setDirty(true);
    }

    public void insert(K key, V value, Transaction<K, V> tx) {
        getLeafEntries().insert(key, value, tx);
        setDirty(true);
    }

    @SuppressWarnings("unchecked")
    public V delete(K key, Transaction<K, V> tx) {
        V value = (V) getEntry(key, tx);
        if (value == null)
            return null;

        getLeafEntries().delete(key, tx);
        setDirty(true);
        return value;
    }

    /**
     * @param value can be null. In that case equality is not checked.
     */
    public PageValue<?> removeContents(MVKeyRange<K> range) {
        PageValue<?> v = indexContents.remove(range);
        setDirty(true);
        return v;
    }

    public void collectPagesAtHeight(int height, Collection<VisualizablePage<K, V>> result,
        Owner owner) {
        collectPagesAtHeight(height, null, result, owner);
    }

    public void collectPagesAtHeight(int height, Integer version,
        Collection<VisualizablePage<K, V>> result, Owner owner) {
        if (version != null && !isAlive(version)) {
            // Break if version is defined and this page is not alive
            return;
        }
        if (getHeight() == height) {
            result.add(this);
        } else {
            if (getHeight() > height) {
                // Descend
                for (Entry<MVKeyRange<K>, PageValue<?>> entry : indexContents.entrySet()) {
                    PageID childID = (PageID) entry.getValue();
                    P child = dbConfig.getPageBuffer().fixPage(childID, factory, false, owner);
                    child.collectPagesAtHeight(height, version, result, owner);
                    dbConfig.getPageBuffer().unfix(child, owner);
                }
            }
        }
    }

    public boolean contains(K key, Transaction<K, V> tx) {
        assert isLeafPage();
        return getLeafEntries().get(key, tx) != null;
    }

    public boolean containsPage(K key, int version) {
        if (isLeafPage()) {
            return getKeyRange().contains(key, version);
        } else {
            for (MVKeyRange<K> range : indexContents.keySet()) {
                if (range.contains(key, version)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Convenience method from Page. Does not follow proper page fixing
     * policies. Must only be used by the visualization program.
     */
    @Override
    public List<VisualizablePage<K, V>> getChildren() {
        if (isLeafPage()) {
            // Leaves have no children
            return new ArrayList<VisualizablePage<K, V>>();
        }
        List<VisualizablePage<K, V>> res = new ArrayList<VisualizablePage<K, V>>();
        for (PageValue<?> value : indexContents.values()) {
            PageID pageID = (PageID) value;
            P page = dbConfig.getPageBuffer().fixPage(pageID, factory, false, tree.internalOwner);
            res.add(page);
            dbConfig.getPageBuffer().unfix(page, tree.internalOwner);
        }
        return res;
    }

    @Override
    public TextLine[] getPageData(int version, TreeDrawStyle scheme) {
        assert !isLeafPage();
        TextLine[] data = new TextLine[indexContents.size()];
        int c = 0;
        for (Entry<MVKeyRange<K>, PageValue<?>> entry : indexContents.entrySet()) {
            MVKeyRange<K> range = entry.getKey();
            PageID pageID = (PageID) entry.getValue();
            String showPageID = PREFIX_PAGE + String.valueOf(pageID.intValue());
            data[c++] = new TextLine(String.format("(%s, %s)", range, showPageID), range
                .containsVersion(version) ? scheme.getPageTextColor() : scheme
                .getDeadPageTextColor(), range, pageID);
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    protected boolean getRange(KeyRange<K> range, Callback<Pair<K, V>> callback,
        Transaction<K, V> tx) {
        Queue<PageID> queue = new ArrayDeque<PageID>();

        final PageBuffer buffer = dbConfig.getPageBuffer();
        // Latch the page
        buffer.readLatch(getPageID(), tx);
        queue.add(getPageID());

        while (!queue.isEmpty()) {
            PageID pageID = queue.remove();
            // Enchancement: if the page in the queue is this page (root), do
            // not take an extra fix
            P page = pageID.equals(getPageID()) ? (P) this : buffer.fixPage(pageID, factory,
                false, tx);
            assert page != null;
            assert page.getKeyRange().containsVersion(tx.getReadVersion());

            if (page.isLeafPage()) {
                if (!page.getLeafEntries().getRange(range, tx, callback)) {
                    // Stop search. Dispose of the current page, and
                    // all pages in the buffer.
                    buffer.unlatch(page, tx);
                    // Enhancement: this page (root) does not have an
                    // extra fix, so do not unfix it
                    if (page != this)
                        buffer.unfix(page, tx);
                    // Unlatch the page IDs in the queue
                    buffer.disposePages(queue, true, false, tx);
                    return false;
                }
            } else {
                for (Entry<MVKeyRange<K>, PageValue<?>> entry : page.indexContents.entrySet()) {
                    MVKeyRange<K> entryRange = entry.getKey();
                    if (entryRange.containsVersion(tx.getReadVersion())
                        && entryRange.overlaps(range)) {
                        PageID nextPageID = (PageID) entry.getValue();
                        buffer.readLatch(nextPageID, tx);
                        queue.add(nextPageID);
                    }
                }
            }
            // Unfix page after using it
            buffer.unlatch(page, tx);
            // Enhancement: this page (root) does not have an extra fix, so do
            // not unfix it
            if (page != this)
                buffer.unfix(page, tx);
        }
        // All callbacks returned true
        return true;
    }

    protected Transaction<K, V> getLatestTX() {
        return new DummyTransaction<K, V>(tree.getLatestVersion(), tree.internalOwner
            .getOwnerID(), tree.internalOwner);
    }

    /**
     * Returns the child page that contains the given key. The page is fixed
     * to the buffer and needs to be unfixed by the caller.
     */
    @Override
    public P getChild(K key, Owner owner) {
        assert !isLeafPage();
        int version = tree.getLatestVersion();
        PageID childID = (PageID) getEntry(key, getLatestTX());
        assert childID != null;
        P child = dbConfig.getPageBuffer().fixPage(childID, factory, false, owner);
        assert child != null;
        assert child.getKeyRange().contains(key, version) : "Child page " + child
            + " does not contain " + key + "@" + version;
        assert child.isAlive();
        return child;
    }

    @Override
    public PageValue<?> getEntry(K key) {
        return getEntry(key, getLatestTX());
    }

    @Override
    public PageValue<?> getEntry(K key, Transaction<K, V> tx) {
        assert getKeyRange().contains(key, tx.getReadVersion()) : String.format(
            "Out of key range when searching for entry," + " %s not in %s at version %d", key,
            getKeyRange(), tx.getReadVersion());

        if (isLeafPage()) {
            return getLeafEntries().get(key, tx);
        } else {

            for (MVKeyRange<K> range : indexContents.keySet()) {
                if (range.contains(key, tx.getReadVersion())) {
                    return indexContents.get(range);
                }
            }
            return null;
        }
    }

    @Override
    protected void clearContents() {
        indexContents.clear();
        setDirty(true);
    }

    @Override
    public int getSingleEntrySize() {
        assert !isLeafPage();
        int size = 0;
        // Key range size
        size += getKeyRange().getByteDataSize();
        // Data storage size
        size += PageID.PROTOTYPE.getByteDataSize();
        return size;
    }

    @Override
    protected MVKeyRange<K> loadSingleEntry(ByteBuffer data, int index, int entryCount) {
        assert !isLeafPage();
        assert data.remaining() >= getSingleEntrySize() : data.remaining() + " < "
            + getSingleEntrySize();

        // Read key range key
        MVKeyRange<K> range = null;
        if (isLeafPage()) {
            K key = getKeyRange().getMin().readFromBytes(data);
            IntegerKeyRange vr = getKeyRange().getVersionRange().readFromBytes(data);
            range = new MVKeyRange<K>(key, vr);
        } else {
            range = getKeyRange().readFromBytes(data);
        }

        // Read stored value
        PageValue<?> value = null;
        if (isLeafPage())
            value = dbConfig.getValuePrototype().readFromBytes(data);
        else
            value = PageID.PROTOTYPE.readFromBytes(data);

        assert value != null;
        putContents(range, value);

        return range;
    }

    @Override
    protected Object saveSingleEntry(ByteBuffer data, int index, Object position) {
        assert !isLeafPage();
        assert data.remaining() >= getSingleEntrySize() : data.remaining() + " < "
            + getSingleEntrySize();

        Map.Entry<MVKeyRange<K>, PageValue<?>> entry = null;
        if (index == 0) {
            // Store the first key
            entry = indexContents.firstEntry();
        } else {
            entry = indexContents.higherEntry(lastSavedKey);
        }
        assert entry != null;

        MVKeyRange<K> range = entry.getKey();
        if (isLeafPage()) {
            range.getMin().writeToBytes(data);
            range.getVersionRange().writeToBytes(data);
        } else {
            range.writeToBytes(data);
        }

        // Write stored contents
        PageValue<?> value = entry.getValue();
        assert value != null;
        value.writeToBytes(data);

        lastSavedKey = range;
        return null;
    }

    @Override
    public boolean contains(K key) {
        return indexContents.containsKey(key);
    }

    public boolean traverseMVPages(Predicate<MVKeyRange<K>> predicate,
        Callback<Page<K, V>> operation, Owner owner) {

        // Check if this page matches
        if (predicate.matches(getKeyRange(), getHeight())) {
            if (!operation.callback(this))
                return false;
        }

        if (getHeight() > 1 && predicate.continueTraversal(getKeyRange(), getHeight())) {
            int childHeight = getHeight() - 1;
            // Go through entries in this page
            for (Entry<MVKeyRange<K>, PageValue<?>> entry : indexContents.entrySet()) {
                // Check if an entry matches
                if (predicate.continueTraversal(entry.getKey(), childHeight)) {
                    PageID childID = (PageID) entry.getValue();
                    P child = dbConfig.getPageBuffer().fixPage(childID, factory, false, owner);
                    boolean doCont = child.traverseMVPages(predicate, operation, owner);
                    dbConfig.getPageBuffer().unfix(child, owner);
                    if (!doCont)
                        return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean processEntries(Callback<Pair<KeyRange<K>, PageValue<?>>> callback) {
        if (isLeafPage()) {
            for (Triple<MVKeyRange<K>, Boolean, V> entry : Iterables.get(getLeafEntries()
                .logicalIterator())) {
                if (!callback.callback(new Pair<KeyRange<K>, PageValue<?>>(entry.getFirst(),
                    entry.getThird())))
                    return false;
            }
        } else {
            for (Entry<MVKeyRange<K>, PageValue<?>> entry : indexContents.entrySet()) {
                if (!callback.callback(new Pair<KeyRange<K>, PageValue<?>>(entry.getKey(), entry
                    .getValue())))
                    return false;
            }
        }
        return true;
    }

    @Override
    public boolean processLeafEntries(int version, KeyRange<K> range,
        Callback<Pair<K, V>> callback) {
        assert isLeafPage();
        if (range == null)
            range = dbConfig.getKeyPrototype().getEntireRange();
        return getLeafEntries().getRange(range,
            new DummyTransaction<K, V>(version, -1, tree.internalOwner), callback);
    }

    @Override
    public PageID findChildPointer(K key) {
        if (isLeafPage())
            throw new UnsupportedOperationException("Not supported for child pages");
        return (PageID) getEntry(key);
    }

    @Override
    public List<PageValue<?>> getEntries() {
        return CollectionUtils.entrySetToValueList(indexContents.entrySet());
    }

    @Override
    public boolean containsChild(PageID childID) {
        assert !isLeafPage();
        for (Entry<?, PageValue<?>> entry : indexContents.entrySet()) {
            if (childID.equals(entry.getValue()))
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s, height: %d, entries: %d(%d)/%d (%.1f %%), (%sleaf)", getName(),
            getHeight(), getEntryCount(), getLiveEntryCount(tree.getCommittedVersion()),
            getPageEntryCapacity(), getFillRatio() * 100, isLeafPage() ? "" : "not ");
    }

}
