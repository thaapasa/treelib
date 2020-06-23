package fi.hut.cs.treelib.mvbt;

import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.btree.BTreePage;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.hut.cs.treelib.common.VersionedKey;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.tuska.util.Callback;
import fi.tuska.util.Holder;
import fi.tuska.util.Pair;

/**
 * Versioned B-tree used in the concurrent multiversion B-tree (CMVBT). Based
 * on a B-tree that uses versioned keys as keys and update markers as values.
 * 
 * @author thaapasa
 * 
 * @param <K> the underlying real key type
 * @param <V> the underlying real value type
 */
public class VersionedBTree<K extends Key<K>, V extends PageValue<?>> extends
    BTree<VersionedKey<K>, UpdateMarker<V>> {

    private static final Logger log = Logger.getLogger(VersionedBTree.class);

    public VersionedBTree(double minFillRate, PageID infoPageID,
        DatabaseConfiguration<VersionedKey<K>, UpdateMarker<V>> dbConfig) {
        super("vbt", "VBT", infoPageID, dbConfig);
        showLeafPageValues = true;
    }

    protected UpdateMarker<V> findLatestKey(K key, final CMVBTree<K, V>.TransientMapping mapping,
        Transaction<VersionedKey<K>, UpdateMarker<V>> tx) {
        if (log.isDebugEnabled())
            log.debug("Finding latest key " + key + " with versions: " + mapping.stc
                + " (starts " + mapping.startVersions + ")");

        if (mapping.startVersions.size() < 1)
            return null;

        VersionedKey<K> min = new VersionedKey<K>(key, mapping.firstStart);
        VersionedKey<K> max = new VersionedKey<K>(key, mapping.lastStart + 1);

        final SortedMap<Integer, UpdateMarker<V>> foundKeys = new TreeMap<Integer, UpdateMarker<V>>();

        // Do a range query for the entire starting version range, and
        // manually skip entries that are not actually valid versions
        getRange(new KeyRangeImpl<VersionedKey<K>>(min, max),
            new Callback<Pair<VersionedKey<K>, UpdateMarker<V>>>() {
                @Override
                public boolean callback(Pair<VersionedKey<K>, UpdateMarker<V>> entry) {
                    VersionedKey<K> key = entry.getFirst();
                    int ver = key.getVersion();
                    // Check if this is an actually valid version
                    if (!mapping.startVersions.contains(ver))
                        // Return true to continue
                        return true;

                    // This is a valid version. Add the update to the sorted
                    // map.
                    // Find out commit-time version
                    Integer commitVer = mapping.stc.get(ver);
                    assert commitVer != null : "No commitver for startver " + ver;
                    // Add the update to the commit list
                    foundKeys.put(commitVer, entry.getSecond());
                    // True to continue search
                    return true;
                }
            }, tx);

        if (foundKeys.isEmpty())
            return null;

        return foundKeys.get(foundKeys.lastKey());
    }

    protected Pair<K, UpdateMarker<V>> findNextMarker(
        K key,
        final CMVBTree<K, V>.TransientMapping mapping,
        PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>> savedPath,
        Transaction<VersionedKey<K>, UpdateMarker<V>> tx) {
        if (log.isDebugEnabled())
            log.debug("Finding next key " + key + " with versions: " + mapping.stc + " (starts "
                + mapping.startVersions + ")");

        if (mapping.startVersions.size() < 1)
            return null;

        // Range is from this key (max version, does not exist) to the max
        // key, so that the next key is found if there is such a key
        VersionedKey<K> min = new VersionedKey<K>(key, Integer.MAX_VALUE);

        final Holder<K> keyValue = new Holder<K>();
        final SortedMap<Integer, UpdateMarker<V>> foundKeys = new TreeMap<Integer, UpdateMarker<V>>();

        VersionedKey<K> curKey = min;

        while (true) {
            Pair<VersionedKey<K>, UpdateMarker<V>> next = nextEntry(curKey, savedPath, tx);
            if (next == null)
                break;
            curKey = next.getFirst();

            int ver = curKey.getVersion();
            // Check if this is an actually relevant version
            if (!mapping.startVersions.contains(ver)) {
                if (keyValue.isInitialized()) {
                    // Check if we're still at the correct key
                    if (!curKey.getKey().equals(keyValue.getValue())) {
                        // Past the next key, so break out of the loop
                        assert curKey.getKey().compareTo(keyValue.getValue()) > 0;
                        assert !foundKeys.isEmpty();
                        break;
                    }
                }
                continue;
            }

            // Check the key itself
            UpdateMarker<V> marker = next.getSecond();

            if (!keyValue.isInitialized()) {
                // This is the first non-delete update found with key
                // larger than the searched key
                keyValue.setValue(curKey.getKey());
            } else {
                // Check have we gone past all the next keys?
                if (curKey.getKey().compareTo(keyValue.getValue()) > 0) {
                    // We have gathered all entries of this key
                    assert !foundKeys.isEmpty();
                    // Stop search
                    break;
                }
            }

            // This is a valid version. Add the update to the sorted map.
            // Find out commit-time version
            Integer commitVer = mapping.stc.get(ver);
            assert commitVer != null;
            // Add the update to the commit list
            foundKeys.put(commitVer, marker);

            // We can also stop if the last possible start version is
            // encountered
            if (ver >= mapping.lastStart)
                break;
        }

        // Breaked out of loop because we are past the limit (or no more
        // entries found)

        if (foundKeys.isEmpty()) {
            // No more next entries found
            // Clear the saved path
            // new Exception().printStackTrace(System.err);
            return null;
        }
        assert keyValue.isInitialized();
        assert keyValue.getValue() != null;

        return new Pair<K, UpdateMarker<V>>(keyValue.getValue(), foundKeys.get(foundKeys
            .lastKey()));
    }

}
