package fi.hut.cs.treelib.mvbt;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVPage;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.common.AbstractMVTreePage;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.hut.cs.treelib.gui.TreeDrawStyle;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.tuska.util.Filter;
import fi.tuska.util.Triple;
import fi.tuska.util.iterator.FilteringIterator;
import fi.tuska.util.iterator.Iterables;

public class MVBTPage<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMVTreePage<K, V, MVBTPage<K, V>> implements MVPage<K, V, MVBTPage<K, V>>, Component {

    private static final Logger log = Logger.getLogger(MVBTPage.class);
    public static final int PAGE_IDENTIFIER = 0x3ed7ba6e;
    protected final MVBTree<K, V> tree;

    private final SMOPolicy smoPolicy;
    private final UpdateMarker<V> leafValuePrototype;

    /* Initialized in the format() method */
    private MVBTEntryMap<K, V> leafEntries;

    protected MVBTPage(MVBTree<K, V> tree, PageID id) {
        super(tree, id, tree.getDBConfig().getPageSize(), tree.getKeyPrototype(), tree
            .getValuePrototype());

        this.tree = tree;

        this.leafValuePrototype = UpdateMarker.createDelete(tree.getValuePrototype());
        this.smoPolicy = tree.getDBConfig().getSMOPolicy();
        calculateCapacity();
    }

    @Override
    protected MVBTEntryMap<K, V> getLeafEntries() {
        assert isLeafPage();
        assert leafEntries != null;
        return leafEntries;
    }

    /*
     * Initialization
     * -------------------------------------------------------------
     */

    @Override
    protected void calculateCapacity() {
        super.calculateCapacity();
        int minEntries = smoPolicy.getMinEntries(this);
        int splitMin = smoPolicy.getMinEntriesAfterSMO(this);
        int splitMax = smoPolicy.getMaxEntriesAfterSMO(this);

        // Sanity checks:
        if (Configuration.instance().isLimitPageSizes()) {
            assert minEntries >= 1 : minEntries;
            assert splitMin >= 1 : splitMin;
            assert splitMax <= getPageEntryCapacity() : splitMax + "," + getPageEntryCapacity();
        } else {
            if (minEntries < 2)
                throw new IllegalArgumentException(String.format(
                    "Illegal entry count parameters: Min entries (%d) < 2", minEntries));
            assert splitMin > minEntries;
            assert splitMax < getPageEntryCapacity();

            if (2 * splitMin > splitMax)
                throw new IllegalArgumentException(String.format(
                    "Illegal entry count parameters: splitMax (%d) "
                        + "entries cannot be put to two pages (2 * %d = %d)", splitMax, splitMin,
                    2 * splitMin));
        }
    }

    /*
     * Weak/strong version condition checks
     * ------------------------------------------------------
     */

    public boolean isStrongVersionUnderflow(PagePath<K, V, MVBTPage<K, V>> path) {
        assert path.getCurrent() == this;
        // Root does not underflow
        return !isRoot(path) && isStrongVersionUnderflow(getEntryCount());
    }

    public boolean isWeakVersionUnderflow(PagePath<K, V, MVBTPage<K, V>> path) {
        assert path.getCurrent() == this;
        if (!isAlive(tree.getActiveVersion())) {
            return false;
        }
        int aliveCount = getLiveEntryCount(tree.getActiveVersion());
        if (isRoot(path)) {
            if (isLeafPage()) {
                // Leaf root underflows when its empty
                return aliveCount == 0;
            } else {
                // Index root underflows when there are less than two alive
                // child pointers
                return aliveCount < 2;
            }
        } else {
            return aliveCount < smoPolicy.getMinEntries(this);
        }
    }

    public boolean isStrongVersionUnderflow(int entries) {
        return entries < smoPolicy.getMinEntriesAfterSMO(this);
    }

    public boolean isStrongVersionOverflow() {
        return isStrongVersionOverflow(getEntryCount());
    }

    public boolean isStrongVersionOverflow(int entries) {
        return entries > smoPolicy.getMaxEntriesAfterSMO(this);
    }

    /**
     * Active pages are pages that have been created during this version.
     * 
     * @return true if this page is active.
     */
    public boolean isActive() {
        return getKeyRange().getMinVersion() == tree.getActiveVersion();
    }

    public boolean isActive(MVKeyRange<K> range) {
        if (range.getMinVersion() == tree.getActiveVersion()) {
            // Sanity check
            assert range.getMaxVersion() == Integer.MAX_VALUE;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isAlive() {
        return getKeyRange().containsVersion(tree.getActiveVersion());
    }

    /*
     * Consistency checks
     * ----------------------------------------------------------
     */

    @Override
    @SuppressWarnings("unchecked")
    public void checkConsistency(Object... params) {
        assert params.length > 0;
        PagePath<K, V, MVBTPage<K, V>> path = (PagePath<K, V, MVBTPage<K, V>>) params[0];

        int liveVersion = tree.getActiveVersion();

        log.debug("Checking consistency of page " + this);
        assert this == path.getCurrent();
        assert !isWeakVersionUnderflow(path) : "Weak version underflow at node " + getName()
            + " at version " + liveVersion + ": " + getLiveEntryCount(liveVersion)
            + " live entries";
        MVKeyRange<K> range = getKeyRange();
        assert !range.isEmpty();
        assert !range.getVersionRange().isEmpty() : "Version range is empty: "
            + range.getVersionRange() + " at node " + getName();

        if (!isLeafPage() && isAlive(liveVersion)) {
            K minKey = range.getMin();
            for (Entry<MVKeyRange<K>, PageValue<?>> entry : getLiveContents()) {
                MVKeyRange<K> nodeRange = entry.getKey();
                assert nodeRange.getMin().equals(minKey) : "Expecting " + minKey + ", got "
                    + nodeRange;
                minKey = nodeRange.getMax();
            }
            assert minKey.equals(range.getMax()) : "Expecting " + range.getMax() + ", got "
                + minKey;
        }

        // Since MVBT is a DAG, iterating through entire tree actually
        // revisits same children multiple times (when they have many
        // parents). Thus currently only checks the active (live) portion of
        // the tree.
        if (!isLeafPage()) {
            PageBuffer buffer = tree.getPageBuffer();
            PageFactory<MVBTPage<K, V>> factory = tree.getPageFactory();
            for (Entry<MVKeyRange<K>, PageValue<?>> entry : getLiveContents()) {
                MVKeyRange<K> cr = entry.getKey();
                assert cr.containsVersion(liveVersion);
                PageID pageID = (PageID) entry.getValue();
                MVBTPage<K, V> child = buffer.fixPage(pageID, factory, false, tree.internalOwner);
                path.descend(child);
                child.checkConsistency(path);
                path.ascend();
                buffer.unfix(child, tree.internalOwner);
            }
        }
    }

    /**
     * @return an iterator over the live contents
     */
    public Iterable<Entry<MVKeyRange<K>, PageValue<?>>> getLiveContents() {
        assert !isLeafPage();
        return new FilteringIterator<Entry<MVKeyRange<K>, PageValue<?>>>(getContents().entrySet()
            .iterator(), new Filter<Entry<MVKeyRange<K>, PageValue<?>>>() {
            @Override
            public boolean isValid(Entry<MVKeyRange<K>, PageValue<?>> entry) {
                return entry.getKey().containsVersion(tree.getActiveVersion());
            }
        });
    }

    /*
     * Custom settings
     * --------------------------------------------------------
     */

    @Override
    protected int getCustomSettingBits() {
        // No custom settings for MVBT nodes
        return 0;
    }

    @Override
    protected void setCustomSettingBits(int settingBits) {
        // No custom settings for MVBT nodes
    }

    @Override
    protected int getMinEntries() {
        return smoPolicy.getMinEntries(this);
    }

    @Override
    public void printDebugInfo() {
        super.printDebugInfo();
        System.out.println("Live entries @ " + tree.getCommittedVersion() + ": "
            + getLiveEntryCount(tree.getCommittedVersion()));
        if (isLeafPage()) {
            for (Triple<K, Integer, UpdateMarker<V>> entry : leafEntries) {
                System.out.println(entry.getFirst() + "@" + entry.getSecond() + ": "
                    + entry.getThird());
            }
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

    /*
     * Custom --------------------------------------------------------
     */

    /**
     * @param toPage the target to move the entry to
     * @return the new separator entry (movedEntry.maxKey ==
     * newFirstEntry.minKey)
     */
    public K moveFirst(MVBTPage<K, V> toPage) {
        if (isLeafPage()) {
            leafEntries.moveFirst(toPage.leafEntries);
            return leafEntries.firstKey();
        } else {
            MVKeyRange<K> range = indexContents.firstKey();
            PageValue<?> child = removeContents(range);

            toPage.putContents(range, child);
            return indexContents.firstKey().getMin();
        }
    }

    /**
     * @param toPage the target to move the entry to
     * @return the new separator entry (movedEntry.minKey)
     */
    public K moveLast(MVBTPage<K, V> toPage) {
        if (isLeafPage()) {
            K moved = leafEntries.moveLast(toPage.leafEntries);
            return moved;
        } else {
            MVKeyRange<K> range = indexContents.lastKey();
            PageValue<?> child = removeContents(range);

            toPage.putContents(range, child);
            return range.getMin();
        }
    }

    @Override
    protected String getPageSummary(int version) {
        return String.format("e: %d, h: %d, a: %d", getEntryCount(), getHeight(),
            getLiveEntryCount(version));
    }

    /*
     * Leaf pages --------------------------------------------------------
     */

    @Override
    public void format(int height) {
        super.format(height);
        if (isLeafPage()) {
            this.leafEntries = new MVBTEntryMap<K, V>(this);
        }
    }

    @Override
    public TextLine[] getPageData(int version, TreeDrawStyle scheme) {
        if (isLeafPage()) {
            if (Configuration.instance().isShowLogicalPageContents()) {
                StringBuilder s = new StringBuilder();
                List<TextLine> data = new LinkedList<TextLine>();
                for (Triple<MVKeyRange<K>, Boolean, V> e : Iterables.get(leafEntries
                    .logicalIterator())) {
                    s.setLength(0);
                    MVKeyRange<K> range = e.getFirst();
                    KeyRange<IntegerKey> vrange = range.getVersionRange();
                    K key = range.getMin();
                    V value = e.getThird();
                    s.append("(");
                    s.append(key).append(", ").append(vrange);
                    if (Configuration.instance().isShowLeafEntryValues()) {
                        s.append(", ").append(value);
                    }
                    s.append(")");
                    data.add(new TextLine(s.toString(), scheme.getPageTextColor(), range, value));
                }
                return data.toArray(new TextLine[0]);
            }

            TextLine[] data = new TextLine[leafEntries.size()];
            int c = 0;
            StringBuilder s = new StringBuilder();
            for (Triple<K, Integer, UpdateMarker<V>> e : leafEntries) {
                s.setLength(0);
                int ver = e.getSecond();
                K key = e.getFirst();
                s.append(key).append("@").append(ver);
                if (Configuration.instance().isShowLeafEntryValues()) {
                    s.append(" ").append(e.getThird());
                }
                data[c++] = new TextLine(s.toString(), scheme.getPageTextColor(), KeyRangeImpl
                    .getKeyRange(key), e.getThird().getValue());
            }
            return data;
        } else {
            return super.getPageData(version, scheme);
        }
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
    protected MVKeyRange<K> loadSingleEntry(ByteBuffer data, int index, int entryCount) {
        if (isLeafPage()) {

            // Key
            K key = dbConfig.getKeyPrototype().readFromBytes(data);
            // Version
            int version = data.getInt();
            // Update marker
            UpdateMarker<V> marker = leafValuePrototype.readFromBytes(data);

            leafEntries.loadEntry(key, version, marker);

            return new MVKeyRange<K>(key, version);
        } else {
            MVKeyRange<K> range = super.loadSingleEntry(data, index, entryCount);
            assert range != null;
            return range;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object saveSingleEntry(ByteBuffer data, int index, Object position) {
        if (isLeafPage()) {
            Iterator<Triple<K, Integer, UpdateMarker<V>>> iterator = (Iterator<Triple<K, Integer, UpdateMarker<V>>>) position;
            if (iterator == null) {
                assert index == 0;
                iterator = leafEntries.iterator();
            }

            assert iterator.hasNext() : "Entry " + index + " not found for " + this;
            Triple<K, Integer, UpdateMarker<V>> v = iterator.next();

            // Save entry

            // Key
            v.getFirst().writeToBytes(data);
            // Version
            data.putInt(v.getSecond());
            // Update marker
            v.getThird().writeToBytes(data);

            return iterator;
        } else {
            return super.saveSingleEntry(data, index, position);
        }
    }

    public boolean isAdjacentLivePage(MVKeyRange<K> range) {
        int version = tree.getActiveVersion();
        if (!range.containsVersion(version)) {
            return false;
        }
        if (range.getMin().equals(getKeyRange().getMax())) {
            // Page is just after this page
            return true;
        } else if (range.getMax().equals(getKeyRange().getMin())) {
            // Page is just before this page
            return true;
        }
        return false;
    }

    /**
     * @return the fixed live sibling. Caller must unfix the returned page!
     */
    public MVBTPage<K, V> getLiveSibling(MVBTPage<K, V> child, Owner owner) {
        assert !isLeafPage();
        if (log.isDebugEnabled())
            log.debug(String.format("Finding live sibling for page %s", child.getName()));
        for (Entry<MVKeyRange<K>, PageValue<?>> entry : indexContents.entrySet()) {
            MVKeyRange<K> routerRange = entry.getKey();
            if (child.isAdjacentLivePage(routerRange)) {
                PageID pageID = (PageID) entry.getValue();
                MVBTPage<K, V> page = dbConfig.getPageBuffer().fixPage(pageID, factory, false,
                    owner);
                if (page != child) {
                    if (log.isDebugEnabled())
                        log.debug(String.format("Comparing key ranges %s to %s", child
                            .getKeyRange(), page.getKeyRange()));
                    assert child.isAdjacentLivePage(entry.getKey());
                    // Left fixed to buffer
                    return page;
                }
                dbConfig.getPageBuffer().unfix(page, owner);
            }
        }
        throw new AssertionError(String.format("No adjacent live sibling found in parent %s"
            + " for page %s at version %d", getName(), child.getName(), tree.getActiveVersion()));
    }

    public PageValue<?> getFirstAliveChild(boolean removeIfActive) {
        for (Iterator<Entry<MVKeyRange<K>, PageValue<?>>> it = indexContents.entrySet()
            .iterator(); it.hasNext();) {
            Entry<MVKeyRange<K>, PageValue<?>> entry = it.next();
            if (entry.getKey().containsVersion(tree.getActiveVersion())) {
                PageValue<?> res = entry.getValue();
                if (removeIfActive && isActive(entry.getKey())) {
                    it.remove();
                }
                return res;
            }
        }
        return null;
    }

    public PageValue<?> updateAliveIndexEntryWithKey(PageValue<?> value, K key,
        MVKeyRange<K> newRange, boolean mustExist) {
        MVKeyRange<K> oldRange = findContentKey(key, tree.getActiveVersion());
        // oldRange can be null if the entry is not in the page
        if (mustExist) {
            assert oldRange != null;
        }
        // If oldRange is null, cannot call contents.get()
        PageValue<?> entryInThis = oldRange != null ? indexContents.get(oldRange) : null;
        if (entryInThis == null || !entryInThis.equals(value)) {
            assert !mustExist : key + ":" + oldRange + " not in " + indexContents + " or "
                + entryInThis + " != " + value;
            return null;
        }

        // This call dirties this page.
        removeContents(oldRange);
        // This must not change the parent. This page might not be the latest
        // parent of child. This call dirties this page.
        putContents(newRange, value);
        // Child page key range must not change (child page can still be
        // alive)
        return entryInThis;
    }
}
