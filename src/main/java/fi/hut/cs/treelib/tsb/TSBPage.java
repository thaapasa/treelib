package fi.hut.cs.treelib.tsb;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map.Entry;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.AbstractMVTreePage;
import fi.hut.cs.treelib.common.CounterCallback;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.hut.cs.treelib.gui.TreeDrawStyle;
import fi.tuska.util.Pair;
import fi.tuska.util.Quad;
import fi.tuska.util.StorableBitSet;
import fi.tuska.util.Triple;
import fi.tuska.util.iterator.Iterables;

public class TSBPage<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMVTreePage<K, V, TSBPage<K, V>> implements Component {

    public static final int PAGE_IDENTIFIER = 0x75b7ba6e;

    /** Bits 0-15 are reserved, 16-31 can be used by extensions */
    public static final int DEFERRED_SPLIT_BIT = 1 << 16;

    private StorableBitSet committedEntries;
    // Override the tree variable to use the correct subclass type
    private final TSBTree<K, V> tree;
    private TSBEntryMap<K, V> leafEntries;
    private final UpdateMarker<V> leafValuePrototype;
    private boolean hasDeferredSplit;

    protected TSBPage(TSBTree<K, V> tree, PageID id, int pageSize) {
        super(tree, id, pageSize, tree.getKeyPrototype(), tree.getValuePrototype());
        this.tree = tree;
        this.leafValuePrototype = UpdateMarker.createDelete(tree.getValuePrototype());
    }

    @Override
    protected TSBEntryMap<K, V> getLeafEntries() {
        return leafEntries;
    }

    @Override
    protected int getMinEntries() {
        return 1;
    }

    public boolean isDeferredSplit() {
        return hasDeferredSplit;
    }

    public void setDeferredSplit(boolean value) {
        this.hasDeferredSplit = value;
        setDirty(true);
    }

    protected int getMinEntryVersion() {
        int minV = Integer.MAX_VALUE;

        if (isLeafPage()) {
            for (Triple<MVKeyRange<K>, Boolean, V> entry : Iterables.get(leafEntries
                .logicalIterator())) {
                int v = entry.getFirst().getVersionRange().getMin().intValue();
                if (v < minV)
                    minV = v;
            }
        } else {
            for (Entry<MVKeyRange<K>, PageValue<?>> entry : getContents().entrySet()) {
                int v = entry.getKey().getVersionRange().getMin().intValue();
                if (v < minV)
                    minV = v;
            }
        }

        return minV;
    }

    protected void attachChildPage(TSBPage<K, V> child) {
        assert !isLeafPage();
        putContents(child.getKeyRange(), child.getPageID());
    }

    @Override
    public boolean contains(K key, Transaction<K, V> tx) {
        assert isLeafPage();
        return leafEntries.get(key, tx) != null;
    }

    protected K getMidEntryKey() {
        assert isLeafPage();
        return leafEntries.getMidKey();
    }

    /**
     * Return the single version current utilization SVCU<sub>page</sub>, as
     * defined by [Lomet 2008].
     * 
     * @param tx the transaction
     * @return the single version current utilization
     */
    public double getSingleVersionCurrentUtilization(Transaction<K, V> tx) {
        // [Lomet 2008] We define single version current utilization for a
        // page (SVCUpage) as the size of the page's current data divided by
        // the page size (both in bytes).

        // Actually, just divide alive entry count by capacity, as all entries
        // are the same size
        return (double) getAliveEntryCount(tx) / (double) getPageEntryCapacity();
    }

    /**
     * Moves all historical entries to sibling; leaving all newer entries to
     * this page
     */
    protected void splitLeafEntriesTo(TSBPage<K, V> sibling, int splitTime) {
        assert isLeafPage();
        assert sibling.isLeafPage();
        assert sibling.leafEntries != null;
        assert sibling.leafEntries.isEmpty();

        // leafEntries.split() creates a new set with all new entries and
        // leaves old entries in place
        TSBEntryMap<K, V> oldEntries = this.leafEntries;
        TSBEntryMap<K, V> newerEntries = oldEntries.split(splitTime);

        // Old entries go to sibling
        sibling.leafEntries = oldEntries;
        // New entries to this page
        this.leafEntries = newerEntries;
    }

    public void updateCommittedTimestamps() {
        if (isLeafPage()) {
            assert leafEntries != null;
            leafEntries.updateAllTemporaries();
        }
    }

    public int getAliveEntryCount(Transaction<K, V> tx) {
        if (isLeafPage()) {
            CounterCallback<Pair<K, V>> counter = new CounterCallback<Pair<K, V>>();
            getLeafEntries().getRange(dbConfig.getKeyPrototype().getEntireRange(), tx, counter);
            return (int) counter.getCount();
        } else {
            int count = 0;
            for (MVKeyRange<K> range : indexContents.keySet()) {
                if (range.containsVersion(tx.getReadVersion())) {
                    count++;
                }
            }
            return count;
        }
    }

    /**
     * Returns the entry key that is closest to the given index position; only
     * picks entries whose version range is the same as the given version
     * range.
     * 
     * @return the entry key to be used to split pages; the given key should
     * go to the sibling page; and the smaller keys should remain in this
     * page.
     */
    protected K selectClosestUnsplitKey(int index, Transaction<K, V> tx) {
        assert !isLeafPage();
        assert getAliveEntryCount(tx) > 0;
        KeyRange<IntegerKey> versionRange = getKeyRange().getVersionRange();
        K foundKey = null;
        boolean found = false;
        int distance = Integer.MAX_VALUE;

        int c = 0;
        for (Entry<MVKeyRange<K>, PageValue<?>> entry : getContents().entrySet()) {
            // Get entry key range
            MVKeyRange<K> r = entry.getKey();

            if (r.getVersionRange().equals(versionRange)) {
                // Range matches
                int eDist = Math.abs(index - c);
                if (!found || eDist < distance) {
                    distance = eDist;
                    found = true;
                    // Simple solution: foundKey = r.getMin();
                    // Need to adjust; if this is first entry, then return the
                    // max key so that this entry remains in the page (as the
                    // only entry)
                    foundKey = (c == 0) ? r.getMax() : r.getMin();
                }
            }
            c++;
        }
        assert found;
        return foundKey;
    }

    /**
     * @return the starting version of the oldest current (alive) entry
     */
    protected int getOldestCurrentUpdateVersion(int version) {
        // Used for index pages
        assert !isLeafPage();

        int oldestFound = -1;
        boolean found = false;
        // Go through all entries
        for (Entry<MVKeyRange<K>, PageValue<?>> entry : getContents().entrySet()) {
            // Get entry key range
            MVKeyRange<K> r = entry.getKey();
            // Check if this is an alive entry (lasts after the given version)
            if (r.getMaxVersion() > version) {
                int curV = r.getMinVersion();
                if (!found || curV < oldestFound) {
                    oldestFound = curV;
                    found = true;
                }
            }
        }
        // If no alive entries are found, return the min version
        return found ? oldestFound : getKeyRange().getMinVersion();
    }

    /**
     * @return the latest version during which an update was made to the page;
     * that is, the version that separates two different values with same key.
     */
    protected int getLastUpdateVersion(Transaction<K, V> tx) {
        // Used for leaf pages
        assert isLeafPage();
        int version = tx.getReadVersion();
        K last = null;
        int maxFound = -1;
        boolean found = false;
        // Go through all entries
        for (Triple<MVKeyRange<K>, Boolean, V> entry : Iterables.get(leafEntries
            .logicalIterator())) {
            // Skip temporary entries (== !isCommitted)
            if (!entry.getSecond())
                continue;

            // Get entry key range
            MVKeyRange<K> r = entry.getFirst();
            // Check if this is an update (the previous entry had the same
            // key)
            if (last != null && last.equals(r.getMin()) && r.getMaxVersion() >= version) {
                // This update happened at the start of this entry's version
                // range
                int curV = r.getMinVersion();
                if (!found || curV > maxFound) {
                    maxFound = curV;
                    found = true;
                }
            }
            last = r.getMin();
        }
        // If no entries are found, then none are alive at version, and we can
        // return the queried version
        return found ? maxFound : version;
    }

    /**
     * @param child the child page, whose key range must be already updated.
     * @param oldRange the old range in this parent's router
     */
    protected void updateChildRouter(TSBPage<K, V> child, MVKeyRange<K> oldRange) {
        assert !isLeafPage();
        // Remove old router
        PageID pageID = (PageID) removeContents(oldRange);
        assert pageID.equals(child.getPageID()) : pageID + " != " + child.getPageID() + " ("
            + child + "@" + this + ")";
        assert !isFull();
        // Add new router
        attachChildPage(child);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void checkConsistency(Object... params) {
        assert params.length > 0;
        PagePath<K, V, TSBPage<K, V>> path = (PagePath<K, V, TSBPage<K, V>>) params[0];
        assert path.getCurrent() == this;

        if (isLeafPage()) {
            assert indexContents == null;
            assert leafEntries.size() <= getPageEntryCapacity() : leafEntries.size() + " > "
                + getPageEntryCapacity();
            leafEntries.checkConsistency();
        } else {
            assert leafEntries == null;
            assert getContents().size() <= getPageEntryCapacity() : getContents().size() + " > "
                + getPageEntryCapacity();

            MVKeyRange<K> pageRange = getKeyRange();

            for (Entry<MVKeyRange<K>, PageValue<?>> entry : getContents().entrySet()) {
                MVKeyRange<K> eRange = entry.getKey();
                // TODO: Is this always the case?
                assert pageRange.contains(eRange);

                if (!isLeafPage()) {
                    // Check child page
                    PageID cID = (PageID) entry.getValue();
                    TSBPage<K, V> child = dbConfig.getPageBuffer().fixPage(cID, factory, false,
                        tree.internalOwner);
                    assert child != null;
                    assert child.getKeyRange().equals(eRange) : child + " range != " + eRange
                        + "@" + this;
                    path.descend(child);
                    // Recurse consistency checks
                    child.checkConsistency(path);
                    path.ascend();
                    dbConfig.getPageBuffer().unfix(child, tree.internalOwner);
                }
            }
        }
    }

    @Override
    public void printDebugInfo() {
        super.printDebugInfo();
        if (isLeafPage()) {
            for (Quad<K, Integer, Boolean, UpdateMarker<V>> entry : leafEntries) {
                System.out.println(entry.getFirst() + "@" + (entry.getThird() ? "t" : "")
                    + entry.getSecond() + ": " + entry.getFourth());
            }
            leafEntries.printDebugInfo();
        } else {
            for (Entry<MVKeyRange<K>, PageValue<?>> entry : getContents().entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    @Override
    public int getTypeIdentifier() {
        return PAGE_IDENTIFIER;
    }

    protected void updateCommittedEntriesBitSet() {
        assert isLeafPage();
        assert committedEntries != null;
        assert committedEntries.getSizeInBits() == getPageEntryCapacity();

        // Update the uncommitted entries bit vector. Sets all bits to on or
        // off, so no clearing needed.
        int i = 0;
        for (Quad<K, Integer, Boolean, UpdateMarker<V>> entry : leafEntries) {
            committedEntries.set(i, entry.getThird());
            i++;
        }
    }

    @Override
    public void format(int height) {
        super.format(height);
        if (isLeafPage()) {
            this.committedEntries = new StorableBitSet(getPageEntryCapacity());
            this.leafEntries = new TSBEntryMap<K, V>(tree, dbConfig.getKeyPrototype(), dbConfig
                .getValuePrototype());
        }
    }

    @Override
    protected int getCustomSettingBits() {
        int bits = 0;
        // Deferred split bit used when deferred split policy is used
        bits |= hasDeferredSplit ? DEFERRED_SPLIT_BIT : 0;
        return bits;
    }

    @Override
    protected void setCustomSettingBits(int settingBits) {
        if ((settingBits & DEFERRED_SPLIT_BIT) != 0) {
            hasDeferredSplit = true;
        }
    }

    @Override
    protected int getCustomHeaderSize() {
        // Original header
        int size = super.getCustomHeaderSize();

        if (isLeafPage()) {
            // We have a small problem here: cannot ask for actual capacity
            // (max. number of entries that fit in page) because calculating
            // that requires knowledge of the custom header size. So, we're
            // approximating upwards by page size / record size (there can't
            // be more than that many entries in any case).
            int absMaxEntries = getPageSize() / getSingleEntrySize();
            int nWords = StorableBitSet.getSizeInWords(absMaxEntries);

            // Add the bit vector for storing the information whether an
            // entry is temporary or not
            size += nWords * 4;
        }

        return size;
    }

    @Override
    public int getSingleEntrySize() {
        if (isLeafPage()) {
            int size = 0;

            // Key
            size += dbConfig.getKeyPrototype().getByteDataSize();
            // Version
            size += 4;
            // Update marker
            size += UpdateMarker.createDelete(dbConfig.getValuePrototype()).getByteDataSize();

            return size;
        } else {
            return super.getSingleEntrySize();
        }
    }

    @Override
    protected void loadPageCustomHeader(ByteBuffer pageData) {
        super.loadPageCustomHeader(pageData);

        if (isLeafPage()) {
            // Load the uncommitted entries bit set
            committedEntries = StorableBitSet.readFromBytes(pageData, getPageEntryCapacity());
        }
    }

    @Override
    protected void savePageCustomHeader(ByteBuffer pageData) {
        int wordCount = (int) Math.ceil(getPageEntryCapacity() / 32.0d);
        assert wordCount * 32 >= getPageEntryCapacity();

        // Update the uncommitted entries bit vector
        if (isLeafPage()) {
            updateCommittedEntriesBitSet();
            committedEntries.writeToBytes(pageData);
        }
    }

    @Override
    protected MVKeyRange<K> loadSingleEntry(ByteBuffer data, int index, int entryCount) {
        if (isLeafPage()) {
            assert committedEntries != null;

            // Key
            K key = dbConfig.getKeyPrototype().readFromBytes(data);
            // Version
            int version = data.getInt();
            // Update marker
            UpdateMarker<V> marker = leafValuePrototype.readFromBytes(data);
            boolean isCommitted = committedEntries.get(index);

            leafEntries.loadEntry(key, marker, version, isCommitted);

            return new MVKeyRange<K>(key, version);
        } else {
            assert committedEntries == null;
            MVKeyRange<K> range = super.loadSingleEntry(data, index, entryCount);
            assert range != null;
            return range;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object saveSingleEntry(ByteBuffer data, int index, Object position) {
        if (isLeafPage()) {
            Iterator<Quad<K, Integer, Boolean, UpdateMarker<V>>> iterator = (Iterator<Quad<K, Integer, Boolean, UpdateMarker<V>>>) position;
            if (iterator == null) {
                assert index == 0;
                iterator = leafEntries.iterator();
            }

            assert iterator.hasNext() : "Entry " + index + " not found for " + this;
            Quad<K, Integer, Boolean, UpdateMarker<V>> v = iterator.next();

            // Save entry

            // Key
            v.getFirst().writeToBytes(data);
            // Version
            data.putInt(v.getSecond());
            // Update marker
            v.getFourth().writeToBytes(data);

            return iterator;
        } else {
            return super.saveSingleEntry(data, index, position);
        }
    }

    @Override
    public TextLine[] getPageData(int version, TreeDrawStyle scheme) {
        if (isLeafPage()) {
            TextLine[] data = new TextLine[leafEntries.size()];
            int c = 0;
            for (Quad<K, Integer, Boolean, UpdateMarker<V>> e : leafEntries) {
                boolean isCommitted = e.getThird();
                int ver = e.getSecond();
                K key = e.getFirst();
                String text = key + "@" + (isCommitted ? "" : "t") + ver + " " + e.getFourth();
                data[c++] = new TextLine(text, scheme.getPageTextColor());
            }
            return data;
        } else {
            return super.getPageData(version, scheme);
        }
    }

    protected Iterable<Quad<K, Integer, Boolean, UpdateMarker<V>>> getLeafContents() {
        assert isLeafPage();
        return leafEntries;
    }

    protected TSBEntryMap<K, V> getEntryMap() {
        assert isLeafPage();
        return leafEntries;
    }

    protected void putLeafEntry(K key, int version, boolean temporary, UpdateMarker<V> value) {
        leafEntries.loadEntry(key, value, version, temporary);
        setDirty(true);
    }

    @Override
    public boolean isDirty() {
        if (isLeafPage() && leafEntries.isDirty())
            return true;
        return super.isDirty();
    }

    @Override
    public void setDirty(boolean state) {
        if (state == false && isLeafPage() && leafEntries != null)
            leafEntries.clearDirty();
        super.setDirty(state);

    }
}
