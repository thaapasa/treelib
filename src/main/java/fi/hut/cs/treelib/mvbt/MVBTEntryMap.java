package fi.hut.cs.treelib.mvbt;

import java.util.Iterator;
import java.util.TreeMap;
import java.util.Map.Entry;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.LeafEntryMap;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;
import fi.tuska.util.Triple;
import fi.tuska.util.iterator.AbstractIterator;

public class MVBTEntryMap<K extends Key<K>, V extends PageValue<?>> implements
    LeafEntryMap<K, V>, Iterable<Triple<K, Integer, UpdateMarker<V>>>, Component {

    private final V valuePrototype;

    private int size = 0;

    protected MVBTPage<K, V> page;

    /**
     * Map format: [key] -> [ updates with commit-time ids ]
     */
    private TreeMap<K, TreeMap<Integer, UpdateMarker<V>>> entries = new TreeMap<K, TreeMap<Integer, UpdateMarker<V>>>();

    public MVBTEntryMap(MVBTPage<K, V> page) {
        this.valuePrototype = page.tree.getValuePrototype();
        this.page = page;
        this.size = 0;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public int size() {
        return size;
    }

    public K firstKey() {
        return entries.firstKey();
    }

    @Override
    public V get(K key, Transaction<K, V> tx) {
        TreeMap<Integer, UpdateMarker<V>> map = entries.get(key);
        if (map == null)
            return null;
        int ver = tx.getReadVersion();
        Entry<Integer, UpdateMarker<V>> entry = map.floorEntry(ver);
        if (entry == null)
            return null;
        UpdateMarker<V> marker = entry.getValue();
        return marker.getValue();
    }

    @Override
    public void delete(K key, Transaction<K, V> tx) {
        int ver = tx.getReadVersion();
        TreeMap<Integer, UpdateMarker<V>> map = entries.get(key);
        if (map == null)
            return;
        assert !map.isEmpty();

        Integer last = map.lastKey();
        assert ver >= last : ver + " < " + last;

        UpdateMarker<V> ownMarker = map.get(ver);
        if (ownMarker != null) {
            // We have our own marker here!
            if (ownMarker.isDelete()) {
                // This key has already been deleted by this transaction
                return;
            } else {
                // Delete key added by own transaction
                UpdateMarker<V> removed = map.remove(ver);
                size--;
                assert removed == ownMarker;

                if (map.isEmpty()) {
                    // No entries in the map, no need to add deletion marker
                    // Remove the map from global entries list
                    entries.remove(key);
                } else {
                    // There is an entry that exists before this one
                    addDeletionMarker(map, ver);
                }
                // Deletion complete
                return;
            }
            // Not reached, already returned
        } else {
            // No own marker
            addDeletionMarker(map, ver);
        }
    }

    /**
     * Adds a deletion marker to the version->value map, if the latest version
     * is alive.
     */
    private void addDeletionMarker(TreeMap<Integer, UpdateMarker<V>> map, int version) {
        // There is an entry that exists before this one
        UpdateMarker<V> prev = map.lowerEntry(version).getValue();
        if (prev.isDelete()) {
            // Key is not alive anyway, do nothing
            // Size must be at least 2, because a single deletion marker is
            // not possible
            assert map.size() >= 2;
        } else {
            // Key is alive, so add deletion marker
            map.put(version, UpdateMarker.createDelete(valuePrototype));
            size++;
        }
    }

    @Override
    public void insert(K key, V value, Transaction<K, V> tx) {
        TreeMap<Integer, UpdateMarker<V>> map = entries.get(key);
        if (map == null) {
            map = new TreeMap<Integer, UpdateMarker<V>>();
            entries.put(key, map);
        }
        int ver = tx.getReadVersion();
        if (!map.isEmpty()) {
            Integer lastKey = map.lastKey();
            assert ver >= lastKey : ver + " < " + lastKey;
        }
        UpdateMarker<V> cur = map.get(ver);
        map.put(ver, UpdateMarker.createInsert(value));
        if (cur == null) {
            // Inserting a new marker (otherwise this replaces an old marker)
            size++;
        }
    }

    public void loadEntry(K key, int version, UpdateMarker<V> marker) {
        TreeMap<Integer, UpdateMarker<V>> map = entries.get(key);
        if (map == null) {
            map = new TreeMap<Integer, UpdateMarker<V>>();
            entries.put(key, map);
        }
        map.put(version, marker);
        size++;
    }

    @Override
    public boolean getRange(KeyRange<K> keyRange, Transaction<K, V> tx,
        Callback<Pair<K, V>> callback) {
        K min = keyRange.getMin();
        K max = keyRange.getMax();
        for (Entry<K, TreeMap<Integer, UpdateMarker<V>>> entry : entries.tailMap(min)
            .headMap(max).entrySet()) {
            K key = entry.getKey();
            TreeMap<Integer, UpdateMarker<V>> map = entry.getValue();
            Entry<Integer, UpdateMarker<V>> e = map.floorEntry(tx.getReadVersion());
            if (e != null && !e.getValue().isDelete()) {
                if (!callback.callback(new Pair<K, V>(key, e.getValue().getValue())))
                    return false;
            }
        }
        return true;
    }

    @Override
    public Iterator<Triple<K, Integer, UpdateMarker<V>>> iterator() {
        return new AbstractIterator<Triple<K, Integer, UpdateMarker<V>>>() {

            private Iterator<Entry<K, TreeMap<Integer, UpdateMarker<V>>>> it = entries.entrySet()
                .iterator();

            private K curKey;
            private Iterator<Entry<Integer, UpdateMarker<V>>> curIt;
            private TreeMap<Integer, UpdateMarker<V>> curMap;

            @Override
            protected Triple<K, Integer, UpdateMarker<V>> findNext() {
                while (true) {
                    if (curIt == null || !curIt.hasNext()) {
                        // Retrieve next from back-end it
                        if (!it.hasNext())
                            return null;
                        Entry<K, TreeMap<Integer, UpdateMarker<V>>> entry = it.next();
                        curKey = entry.getKey();
                        curMap = entry.getValue();
                        curIt = curMap.entrySet().iterator();
                    }
                    if (curIt.hasNext()) {
                        Entry<Integer, UpdateMarker<V>> entry = curIt.next();
                        return new Triple<K, Integer, UpdateMarker<V>>(curKey, entry.getKey(),
                            entry.getValue());
                    }
                }
            }

            @Override
            public void doRemove() {
                assert curIt != null;
                curIt.remove();
                if (curMap.isEmpty())
                    it.remove();
            }
        };
    }

    @Override
    public Iterator<Triple<MVKeyRange<K>, Boolean, V>> logicalIterator() {
        return new AbstractIterator<Triple<MVKeyRange<K>, Boolean, V>>() {

            Iterator<Triple<K, Integer, UpdateMarker<V>>> it = iterator();

            Triple<K, Integer, UpdateMarker<V>> lastEntry;

            @Override
            protected Triple<MVKeyRange<K>, Boolean, V> findNext() {
                while (true) {
                    if (lastEntry == null) {
                        if (!it.hasNext())
                            return null;
                        lastEntry = it.next();
                    }
                    // Step 1. lastEntry contains an entry
                    assert lastEntry != null;

                    if (!it.hasNext()) {
                        // No more entries, create an entry directly from
                        // lastEntry
                        Integer stopVer = page.getKeyRange().getMaxVersion();
                        Triple<MVKeyRange<K>, Boolean, V> res = getEntry(lastEntry, stopVer);
                        lastEntry = null;
                        return res;
                    }

                    // Skip deletions
                    if (lastEntry.getThird().isDelete()) {
                        lastEntry = null;
                        // Skip this delete, move on to next entry
                        continue;
                    }

                    // Last entry is a non-temporary update
                    Triple<K, Integer, UpdateMarker<V>> nextEntry = it.next();

                    Triple<MVKeyRange<K>, Boolean, V> res = null;
                    // if (next.key == last.key)
                    if (nextEntry.getFirst().equals(lastEntry.getFirst())) {
                        // Temporary update entry, return it directly
                        res = getEntry(lastEntry, nextEntry.getSecond());
                    } else {
                        // Next entry does not terminate this entry
                        Integer stopVer = page.getKeyRange().getMaxVersion();
                        res = getEntry(lastEntry, stopVer);
                    }
                    assert res != null;
                    lastEntry = nextEntry;
                    return res;
                }
            }

            private Triple<MVKeyRange<K>, Boolean, V> getEntry(
                Triple<K, Integer, UpdateMarker<V>> entry, Integer terminateVersion) {
                K key = lastEntry.getFirst();
                UpdateMarker<V> value = lastEntry.getThird();
                int ver = lastEntry.getSecond();

                return !value.isDelete() ? new Triple<MVKeyRange<K>, Boolean, V>(
                    terminateVersion == null ? new MVKeyRange<K>(key, ver) : new MVKeyRange<K>(
                        key, ver, terminateVersion), true, value.getValue()) : null;
            }

            @Override
            protected void doRemove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    @Override
    public void checkConsistency(Object... params) {
        assert size >= 0;
        int found = 0;
        for (Entry<K, TreeMap<Integer, UpdateMarker<V>>> e : entries.entrySet()) {
            K key = e.getKey();
            assert key != null;
            assert !key.isPrototype();
            TreeMap<Integer, UpdateMarker<V>> versions = e.getValue();
            assert !versions.isEmpty();

            found += versions.size();
        }
        assert size == found;
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();

        b.append("Entries: {");
        boolean first = true;
        for (Entry<K, TreeMap<Integer, UpdateMarker<V>>> e : entries.entrySet()) {
            if (!first) {
                b.append(", ");
            }
            b.append(e.getKey()).append(":");
            b.append(e.getValue());
            first = false;
        }
        b.append("}");

        return b.toString();
    }

    private int moveVersionsWithKey(K key, MVBTEntryMap<K, V> map) {
        TreeMap<Integer, UpdateMarker<V>> entry = entries.remove(key);
        int entryCount = entry.size();
        assert !map.entries.containsKey(key);
        size -= entryCount;
        map.entries.put(key, entry);
        map.size += entryCount;
        return entryCount;
    }

    public K moveFirst(MVBTEntryMap<K, V> map) {
        assert !isEmpty();
        K key = entries.firstKey();
        int amount = moveVersionsWithKey(key, map);
        assert amount == 1;
        return key;
    }

    public K moveLast(MVBTEntryMap<K, V> map) {
        assert !isEmpty();
        K key = entries.lastKey();
        int amount = moveVersionsWithKey(key, map);
        assert amount == 1 : this;
        return key;
    }

    protected void copyAliveTo(MVBTEntryMap<K, V> map, int curVer) {
        for (Iterator<Entry<K, TreeMap<Integer, UpdateMarker<V>>>> it = entries.entrySet()
            .iterator(); it.hasNext();) {
            Entry<K, TreeMap<Integer, UpdateMarker<V>>> entry = it.next();
            K key = entry.getKey();
            TreeMap<Integer, UpdateMarker<V>> versions = entry.getValue();
            assert versions != null;
            Entry<Integer, UpdateMarker<V>> e = versions.lastEntry();
            assert e != null;
            int v = e.getKey();
            assert v <= curVer;
            UpdateMarker<V> marker = e.getValue();
            if (!marker.isDelete()) {
                // Last entry is alive
                // Copy and replace version with curVer (make active copy)
                map.loadEntry(key, curVer, marker);
            } else {
                // Last entry was a deletion, so do not copy this marker to
                // the new page
            }

            if (v == curVer) {
                // The moved entry is active, so it can be removed from this
                // page
                versions.remove(v);
                size--;
                if (versions.isEmpty()) {
                    // This was the only version of this key
                    it.remove();
                }
            }

        }
    }

    @Override
    public void clearDirty() {
        throw new UnsupportedOperationException("Not used for MVBT entry map");
    }

    @Override
    public boolean isDirty() {
        throw new UnsupportedOperationException("Not used for MVBT entry map");
    }
}
