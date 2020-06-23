package fi.hut.cs.treelib.tsb;

import java.util.Iterator;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.TransactionIDManager;
import fi.hut.cs.treelib.common.LeafEntryMap;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;
import fi.tuska.util.Quad;
import fi.tuska.util.Triple;
import fi.tuska.util.iterator.AbstractIterator;

/**
 * A map that manages a list of keys with committed IDs and temporary
 * transaction IDs. The methods find the correct value from the key list based
 * on a given committed version number and a possible temporary transaction ID
 * for updating transactions that need to get their own updates.
 * 
 * @author thaapasa
 */
public class TSBEntryMap<K extends Key<K>, V extends PageValue<?>> implements LeafEntryMap<K, V>,
    Iterable<Quad<K, Integer, Boolean, UpdateMarker<V>>>, Component {

    private static final Logger log = Logger.getLogger(TSBEntryMap.class);

    private final TransactionIDManager<K> manager;
    private final K keyPrototype;
    private final V valuePrototype;
    private boolean dirty = false;

    private int size = 0;

    /**
     * Map format: [key] -> [ [updates with commit-time ids], [updates with
     * temporary ids] ]
     */
    private TreeMap<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>> entries = new TreeMap<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>>();

    public TSBEntryMap(TransactionIDManager<K> manager, K keyPrototype, V valuePrototype) {
        this.manager = manager;
        this.valuePrototype = valuePrototype;
        this.keyPrototype = keyPrototype;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        if (size == 0) {
            assert entries.isEmpty();
            return true;
        } else {
            assert !entries.isEmpty();
            return false;
        }
    }

    @Override
    public void insert(K key, V value, Transaction<K, V> tx) {
        putTemporary(key, UpdateMarker.createInsert(value), tx.getTransactionID());
        manager.notifyTempInserted(key, tx.getTransactionID());
    }

    @Override
    public void delete(K key, Transaction<K, V> tx) {
        removeTemporary(key, tx.getTransactionID());
        manager.notifyTempInserted(key, tx.getTransactionID());
    }

    public void loadEntry(K key, UpdateMarker<V> value, int version, boolean isCommitted) {
        if (isCommitted) {
            putCommitted(key, value, version);
        } else {
            putTemporary(key, value, version);
        }
    }

    public int getEntriesBefore(int commTime) {
        int entryCount = 0;
        for (Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>> e : entries
            .entrySet()) {
            TreeMap<Integer, UpdateMarker<V>> commMap = e.getValue().getFirst();
            entryCount += commMap.headMap(commTime, false).size();
        }
        return entryCount;
    }

    /**
     * Returns a separator so that keys can be separated to [min, mid) and
     * [mid, max) portions.
     * 
     * @return the mid key, which is the first key that will go to the higher
     * page
     */
    public K getMidKey() {
        int targetC = (int) Math.ceil(size / 2.0d);

        boolean first = true;
        int count = 0;
        for (Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>> e : entries
            .entrySet()) {
            count += e.getValue().getFirst().size();
            count += e.getValue().getSecond().size();
            if (count >= targetC && !first) {
                return e.getKey();
            }
            first = false;
        }

        throw new UnsupportedOperationException("Cannot split this page: " + this
            + ", as it contains too few separate entries");
    }

    /**
     * Split moves versions greater than or equal to splitVer and all
     * temporary versions to the new map. Therefore, the only entries left to
     * this map are the old entries.
     * 
     * @return the new entry map
     */
    public TSBEntryMap<K, V> split(int splitTime) {
        TSBEntryMap<K, V> map = new TSBEntryMap<K, V>(manager, keyPrototype, valuePrototype);

        for (Iterator<Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>>> it = entries
            .entrySet().iterator(); it.hasNext();) {
            Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>> e = it
                .next();

            K key = e.getKey();
            TreeMap<Integer, UpdateMarker<V>> commits = e.getValue().getFirst();
            TreeMap<Integer, UpdateMarker<V>> temps = e.getValue().getSecond();

            // Move all temporaries
            for (Entry<Integer, UpdateMarker<V>> t : temps.entrySet()) {
                map.putTemporary(key, t.getValue(), t.getKey());
            }
            size -= temps.size();
            temps.clear();

            // Move some of the commits

            if (true) {
                boolean firstMoved = false;
                boolean currentlyExists = false;
                V currentVal = null;

                for (Iterator<Entry<Integer, UpdateMarker<V>>> it2 = commits.entrySet()
                    .iterator(); it2.hasNext();) {
                    Entry<Integer, UpdateMarker<V>> c = it2.next();
                    int ver = c.getKey();
                    if (ver < splitTime) {
                        // Skip these first entries, they will be left to this
                        // map
                        // Update existence status
                        currentlyExists = !c.getValue().isDelete();
                        currentVal = currentlyExists ? c.getValue().getValue() : null;
                    } else {
                        // Move this version
                        if (!firstMoved) {
                            if (ver != splitTime && currentlyExists) {
                                // Add a compensation record
                                map.putCommitted(key, UpdateMarker.createInsert(currentVal),
                                    splitTime);
                            }
                            firstMoved = true;
                        }
                        map.putCommitted(key, c.getValue(), c.getKey());
                        it2.remove();
                        size--;
                    }
                }

                if (!firstMoved && currentlyExists) {
                    // Add a compensation record
                    map.putCommitted(key, UpdateMarker.createInsert(currentVal), splitTime);
                }
            }

            if (commits.isEmpty()) {
                it.remove();
            }

        }
        return map;
    }

    @Override
    public boolean getRange(KeyRange<K> range, Transaction<K, V> tx, Callback<Pair<K, V>> callback) {

        final K rangeMin = range.getMin();
        final K rangeMax = range.getMax();

        K curKey = rangeMin.previousKey();

        while (true) {
            // Find next key
            Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>> e = entries
                .higherEntry(curKey);
            // Check if the end of the list is reached
            if (e == null)
                return true;

            // Update key and values
            curKey = e.getKey();
            assert curKey.compareTo(rangeMin) >= 0;
            // If we've gone past the end of the range
            if (curKey.compareTo(rangeMax) >= 0)
                return true;

            TreeMap<Integer, UpdateMarker<V>> committedValues = e.getValue().getFirst();
            TreeMap<Integer, UpdateMarker<V>> temporaryValues = e.getValue().getSecond();

            boolean thisProcessed = false;

            if (!temporaryValues.isEmpty()) {
                // Check the temporary IDS
                checkAndConvertTemporaryIDs(curKey, temporaryValues, committedValues);

                if (tx.isUpdating()) {
                    UpdateMarker<V> ownVal = temporaryValues.get(tx.getTransactionID());
                    if (ownVal != null) {
                        // If an update is found with the correct temporary
                        // (transaction) ID, then this key must not be
                        // processed again later (from the committed entries
                        // list)
                        thisProcessed = true;
                        if (!ownVal.isDelete()) {
                            // Only call callback if the last value is not a
                            // deletion
                            if (!callback.callback(new Pair<K, V>(curKey, ownVal.getValue())))
                                return false;
                        }
                    }
                }
            }

            if (!thisProcessed) {
                UpdateMarker<V> value = findCorrectMarker(tx.getReadVersion(), committedValues);
                if (value != null && !value.isDelete()) {
                    if (!callback.callback(new Pair<K, V>(curKey, value.getValue())))
                        return false;
                }
            }
        }
    }

    @Override
    public V get(K key, Transaction<K, V> tx) {
        Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>> keyVals = entries
            .get(key);

        if (keyVals == null)
            return null;

        TreeMap<Integer, UpdateMarker<V>> committedValues = keyVals.getFirst();
        TreeMap<Integer, UpdateMarker<V>> temporaryValues = keyVals.getSecond();

        if (!temporaryValues.isEmpty()) {
            // Check the temporary IDS
            checkAndConvertTemporaryIDs(key, temporaryValues, committedValues);

            if (tx.isUpdating()) {
                UpdateMarker<V> ownVal = temporaryValues.get(tx.getTransactionID());
                if (ownVal != null) {
                    return ownVal.isDelete() ? null : ownVal.getValue();
                }
            }
        }
        UpdateMarker<V> value = findCorrectMarker(tx.getReadVersion(), committedValues);
        // value can be null if all the versions are with higher versions
        return (value == null || value.isDelete()) ? null : value.getValue();
    }

    /**
     * Elements: key-range, isCommitted, value
     */
    @Override
    public Iterator<Triple<MVKeyRange<K>, Boolean, V>> logicalIterator() {
        return new AbstractIterator<Triple<MVKeyRange<K>, Boolean, V>>() {

            Iterator<Quad<K, Integer, Boolean, UpdateMarker<V>>> it = iterator();

            Quad<K, Integer, Boolean, UpdateMarker<V>> lastEntry;

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
                        Triple<MVKeyRange<K>, Boolean, V> res = getEntry(lastEntry, null);
                        lastEntry = null;
                        return res;
                    }

                    // Skip deletions
                    if (lastEntry.getFourth().isDelete()) {
                        lastEntry = null;
                        // Skip this delete, move on to next entry
                        continue;
                    }

                    // Check if the entry is temporary (!isCommitted)
                    if (!lastEntry.getThird()) {
                        // Temporary update entry, return it directly
                        Triple<MVKeyRange<K>, Boolean, V> res = getEntry(lastEntry, null);
                        lastEntry = null;
                        return res;
                    }
                    // Last entry is a non-temporary update

                    Quad<K, Integer, Boolean, UpdateMarker<V>> nextEntry = it.next();

                    Triple<MVKeyRange<K>, Boolean, V> res = null;
                    // if (next.key == last.key && next.isCommitted)
                    if (nextEntry.getFirst().equals(lastEntry.getFirst()) && nextEntry.getThird()) {
                        // Temporary update entry, return it directly
                        res = getEntry(lastEntry, nextEntry.getSecond());
                    } else {
                        // Next entry does not terminate this entry
                        res = getEntry(lastEntry, null);
                    }
                    assert res != null;
                    lastEntry = nextEntry;
                    return res;
                }
            }

            private Triple<MVKeyRange<K>, Boolean, V> getEntry(
                Quad<K, Integer, Boolean, UpdateMarker<V>> entry, Integer terminateVersion) {
                K key = lastEntry.getFirst();
                UpdateMarker<V> value = lastEntry.getFourth();
                int ver = lastEntry.getSecond();
                boolean isCommitted = lastEntry.getThird();

                return !value.isDelete() ? new Triple<MVKeyRange<K>, Boolean, V>(
                    terminateVersion == null ? new MVKeyRange<K>(key, ver) : new MVKeyRange<K>(
                        key, ver, terminateVersion), isCommitted, value.getValue()) : null;
            }

            @Override
            protected void doRemove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    /**
     * Elements: key, version, isCommitted, value.
     * 
     * <p>
     * isCommitted is true when the version is a commit-time version; and
     * false when the version is a temporary version
     */
    @Override
    public Iterator<Quad<K, Integer, Boolean, UpdateMarker<V>>> iterator() {
        return new AbstractIterator<Quad<K, Integer, Boolean, UpdateMarker<V>>>() {

            private Iterator<Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>>> it = entries
                .entrySet().iterator();
            private int state = 0;
            private K curKey = null;
            private Iterator<Entry<Integer, UpdateMarker<V>>> temporaries;
            private Iterator<Entry<Integer, UpdateMarker<V>>> committed;
            private TreeMap<Integer, UpdateMarker<V>> tempMap;
            private TreeMap<Integer, UpdateMarker<V>> commMap;

            @Override
            protected Quad<K, Integer, Boolean, UpdateMarker<V>> findNext() {
                while (true) {
                    if (state == 0) {
                        if (!it.hasNext())
                            return null;
                        Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>> e = it
                            .next();
                        curKey = e.getKey();
                        commMap = e.getValue().getFirst();
                        tempMap = e.getValue().getSecond();
                        committed = commMap.entrySet().iterator();
                        temporaries = tempMap.entrySet().iterator();
                        state = 1;
                    }
                    if (state == 1) {
                        if (committed.hasNext()) {
                            Entry<Integer, UpdateMarker<V>> e = committed.next();
                            return new Quad<K, Integer, Boolean, UpdateMarker<V>>(curKey, e
                                .getKey(), true, e.getValue());
                        } else {
                            state = 2;
                        }
                    }
                    if (state == 2) {
                        if (temporaries.hasNext()) {
                            Entry<Integer, UpdateMarker<V>> e = temporaries.next();
                            return new Quad<K, Integer, Boolean, UpdateMarker<V>>(curKey, e
                                .getKey(), false, e.getValue());
                        } else {
                            // Browse to next key
                            state = 0;
                        }
                    }
                }
            }

            @Override
            public void doRemove() {
                switch (state) {
                case 1:
                    // Committed entry
                    committed.remove();
                    break;
                case 2:
                    // Temporary entry
                    temporaries.remove();
                    break;
                default:
                    throw new IllegalStateException("Illegal state " + state);
                }
                if (tempMap.isEmpty() && commMap.isEmpty())
                    it.remove();
                size--;
            }
        };
    }

    private void putTemporary(K key, UpdateMarker<V> value, int transactionID) {
        Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>> keyVals = getKeyValues(key);
        dirty = true;

        TreeMap<Integer, UpdateMarker<V>> temporaryMap = keyVals.getSecond();
        UpdateMarker<V> marker = temporaryMap.get(transactionID);
        temporaryMap.put(transactionID, value);
        if (log.isDebugEnabled()) {
            if (marker != null) {
                log.debug("Overwriting " + key + ": " + marker + " with " + value + " @"
                    + transactionID);
            } else {
                log.debug("Inserting " + key + ": " + value + " @" + transactionID);
            }
        }

        if (marker == null) {
            size++;
        }
    }

    private void removeTemporary(K key, int transactionID) {
        Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>> keyVals = getKeyValues(key);

        TreeMap<Integer, UpdateMarker<V>> temporaryMap = keyVals.getSecond();
        UpdateMarker<V> marker = temporaryMap.get(transactionID);
        dirty = true;
        if (marker == null) {
            putTemporary(key, UpdateMarker.createDelete(valuePrototype), transactionID);
        } else {
            // We need to replace the existing entry if there is an earlier
            // existing version
            boolean curExists = false;
            TreeMap<Integer, UpdateMarker<V>> commitMap = keyVals.getFirst();
            Entry<Integer, UpdateMarker<V>> last = commitMap.lastEntry();
            if (last != null) {
                // An entry exists if the last committed value is not a
                // deletion
                curExists = !last.getValue().isDelete();
            }

            if (curExists) {
                // Need to replace old own entry with a deletion marker
                temporaryMap.put(transactionID, UpdateMarker.createDelete(valuePrototype));
                log.debug("Replacing old marker for key " + key + ": " + marker
                    + " with a deletion marker");
                // Size remains the same
            } else {
                // Can just remove the old own entry
                temporaryMap.remove(transactionID);
                log.debug("Removing the old marker for key " + key + ": " + marker);
                size--;
                if (temporaryMap.isEmpty() && commitMap.isEmpty()) {
                    // This was the last update to this key
                    entries.remove(key);
                }
            }
        }
    }

    private void putCommitted(K key, UpdateMarker<V> value, int commitTime) {
        Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>> keyVals = getKeyValues(key);

        TreeMap<Integer, UpdateMarker<V>> commitMap = keyVals.getFirst();
        UpdateMarker<V> marker = commitMap.get(commitTime);
        assert marker == null;
        commitMap.put(commitTime, value);
        if (log.isDebugEnabled()) {
            log.debug("Inserting committed " + key + ": " + value + " @" + commitTime);
        }

        size++;
    }

    private Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>> getKeyValues(
        K key) {
        Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>> keyVals = entries
            .get(key);
        if (keyVals == null) {
            keyVals = new Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>(
                new TreeMap<Integer, UpdateMarker<V>>(), new TreeMap<Integer, UpdateMarker<V>>());
            entries.put(key, keyVals);
        }
        return keyVals;
    }

    private UpdateMarker<V> findCorrectMarker(int version,
        TreeMap<Integer, UpdateMarker<V>> committedValues) {
        Entry<Integer, UpdateMarker<V>> e = committedValues.floorEntry(version);
        return e != null ? e.getValue() : null;
    }

    public void updateAllTemporaries() {
        for (Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>> e : entries
            .entrySet()) {
            K key = e.getKey();

            TreeMap<Integer, UpdateMarker<V>> committedValues = e.getValue().getFirst();
            TreeMap<Integer, UpdateMarker<V>> temporaryValues = e.getValue().getSecond();

            if (!temporaryValues.isEmpty()) {
                checkAndConvertTemporaryIDs(key, temporaryValues, committedValues);
            }
        }
    }

    private void checkAndConvertTemporaryIDs(K key,
        TreeMap<Integer, UpdateMarker<V>> temporaryValues,
        TreeMap<Integer, UpdateMarker<V>> committedValues) {

        // Check if there are any temporary versions at all
        if (temporaryValues.isEmpty())
            return;

        for (Iterator<Entry<Integer, UpdateMarker<V>>> it = temporaryValues.entrySet().iterator(); it
            .hasNext();) {
            Entry<Integer, UpdateMarker<V>> entry = it.next();
            Integer tempID = entry.getKey();
            // Try to find the commit time version of this transaction
            Integer commitVer = manager.getCommitVersion(tempID);
            if (commitVer != null) {
                committedValues.put(commitVer, entry.getValue());

                // Remove the temporary entry
                it.remove();

                manager.notifyTempConverted(key, tempID, commitVer);
                dirty = true;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();

        b.append("Entries: {");
        boolean first = true;
        for (Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>> e : entries
            .entrySet()) {
            if (!first) {
                b.append(", ");
            }
            b.append(e.getKey()).append(":");
            b.append(e.getValue().getFirst());
            b.append("&t:").append(e.getValue().getSecond());
            first = false;
        }
        b.append("}");

        return b.toString();
    }

    @Override
    public void checkConsistency(Object... params) {
        assert size >= 0;
        int found = 0;
        for (Entry<K, Pair<TreeMap<Integer, UpdateMarker<V>>, TreeMap<Integer, UpdateMarker<V>>>> e : entries
            .entrySet()) {
            K key = e.getKey();
            assert key != null;
            assert !key.isPrototype();
            TreeMap<Integer, UpdateMarker<V>> committedValues = e.getValue().getFirst();
            TreeMap<Integer, UpdateMarker<V>> temporaryValues = e.getValue().getSecond();
            assert !committedValues.isEmpty() || !temporaryValues.isEmpty();

            found += committedValues.size();
            found += temporaryValues.size();
        }
        assert size == found;
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
    }

    @Override
    public void clearDirty() {
        this.dirty = false;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

}
