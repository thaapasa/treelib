package fi.hut.cs.treelib.mvbt;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.btree.BTreePage;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.TreeShortcuts;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.hut.cs.treelib.common.VersionedKey;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class CMVBTOperations<K extends Key<K>, V extends PageValue<?>> {

    private static final Logger log = Logger.getLogger(CMVBTOperations.class);

    private final CMVBTree<K, V> cmvbt;
    private final TMVBTree<K, V> tmvbt;
    private final VersionedBTree<K, V> vbt;
    private final Transaction<VersionedKey<K>, UpdateMarker<V>> vbtTX;

    private boolean maintenanceTXRunning = false;

    protected CMVBTOperations(CMVBTree<K, V> cmvbt, TMVBTree<K, V> tmvbt, VersionedBTree<K, V> vbt) {
        this.cmvbt = cmvbt;
        this.tmvbt = tmvbt;
        this.vbt = vbt;
        this.vbtTX = new DummyTransaction<VersionedKey<K>, UpdateMarker<V>>(vbt.internalOwner);
    }

    public boolean insert(K key, V value, Transaction<K, V> tx) {
        VersionedKey<K> vk = new VersionedKey<K>(key, tx.getTransactionID());
        UpdateMarker<V> marker = UpdateMarker.createInsert(value);

        // VBT has been configured to automatically overwrite entries with
        // identical key
        return TreeShortcuts.insert(vbt, vk, marker, vbtTX);
    }

    public boolean delete(K key, Transaction<K, V> tx) {
        VersionedKey<K> vk = new VersionedKey<K>(key, tx.getTransactionID());
        UpdateMarker<V> marker = UpdateMarker.createDelete(cmvbt.getValuePrototype());

        // VBT has been configured to automatically overwrite entries with
        // identical key
        return TreeShortcuts.insert(vbt, vk, marker, vbtTX);
    }

    public V get(K key, Transaction<K, V> tx) {
        StatisticsLogger stats = cmvbt.getStatisticsLogger();
        int version = tx.getReadVersion();
        int transientID = tx.getTransactionID();
        if (tx.isReadOnly() && cmvbt.isStable(version)) {
            log.debug("Version " + version + " is stable, querying from TMVBT");
            stats.log(Operation.OP_VERSION_STABLE);
            // Version is stable, so read it directly from the TMVBT
            return tmvbt.get(key, tx);
        } else {
            // Need to check VBT for updates
            stats.log(Operation.OP_VERSION_TRANSIENT);
            log.debug("Version " + version + " is transient, querying first VBT, then TMVBT");
            UpdateMarker<V> marker = vbt.findLatestKey(key, transientID >= 0 ? cmvbt
                .getTransientMappingForUpdatingTX(version, transientID) : cmvbt
                .getTransientMappingForReadTX(version), vbtTX);
            if (marker != null) {
                // If marker at VBT is a delete, then return null (key has
                // been deleted)
                return marker.isDelete() ? null : marker.getValue();
            } else {
                // No updates in VBT, find key from TMVBT
                return tmvbt.get(key, tx);
            }
        }
    }

    public boolean getRange(KeyRange<K> range, Callback<Pair<K, V>> callback, Transaction<K, V> tx) {
        PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>> vbtPath = new PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>>(
            true);
        PagePath<K, V, MVBTPage<K, V>> tmvbtPath = new PagePath<K, V, MVBTPage<K, V>>(true);
        boolean result = getRange(range, callback, vbtPath, tmvbtPath, tx);

        tmvbt.getPageBuffer().unfix(tmvbtPath, tx);
        vbt.getPageBuffer().unfix(vbtPath, tx);
        return result;
    }

    private boolean getRange(
        KeyRange<K> range,
        Callback<Pair<K, V>> callback,
        PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>> vbtPath,
        PagePath<K, V, MVBTPage<K, V>> tmvbtPath, Transaction<K, V> tx) {

        int version = tx.getReadVersion();
        StatisticsLogger stats = cmvbt.getStatisticsLogger();
        if (tx.isReadOnly() && cmvbt.isStable(version)) {
            log.debug("Version " + version + " is stable, querying from TMVBT");
            stats.log(Operation.OP_VERSION_STABLE);
            // Version is stable, so read it directly from the TMVBT
            return tmvbt.getRange(range, callback, tx);
        } else {
            stats.log(Operation.OP_VERSION_TRANSIENT);
            log.debug("Version " + version + " is not stable, querying from TMVBT + VBT");
            CMVBTree<K, V>.TransientMapping mapping = tx.isReadOnly() ? cmvbt
                .getTransientMappingForReadTX(version) : cmvbt.getTransientMappingForUpdatingTX(
                version, tx.getTransactionID());

            K startKey = range.getMin().previousKey();
            K rangeMax = range.getMax();

            K curKey = startKey;
            while (true) {
                // Scan to next keys of both structures
                Pair<K, UpdateMarker<V>> nextVBT = vbt.findNextMarker(curKey, mapping, vbtPath,
                    vbtTX);
                Pair<K, V> nextTMVBT = tmvbt.nextEntry(curKey, tmvbtPath, tx);
                stats.log(Operation.OP_KEYS_PROCESSED);

                // Check which update is most recent
                int c = 0;
                if (nextVBT == null) {
                    // No more entries in VBT
                    if (nextTMVBT == null) {
                        // Past the end of both structures, no callbacks
                        // returned false
                        return true;
                    }
                    // TMVBT is smaller, and should be checked next
                    c = 1;
                } else if (nextTMVBT == null) {
                    // VBT is smaller, and should be checked next
                    c = -1;
                } else {
                    // Compare the entries
                    c = nextVBT.getFirst().compareTo(nextTMVBT.getFirst());
                }
                if (c < 0) {
                    // VBT < TMVBT, so check the VBT value
                    UpdateMarker<V> marker = nextVBT.getSecond();
                    if (!marker.isDelete()) {
                        // Not a delete, so this is a match!

                        // Next key is nextVBT.getFirst()
                        if (nextVBT.getFirst().compareTo(rangeMax) >= 0)
                            break;
                        if (!callback.callback(new Pair<K, V>(nextVBT.getFirst(), marker
                            .getValue()))) {
                            // Stop indicated
                            return false;
                        }
                    }

                    // Mark current key for scanning to next entry
                    curKey = nextVBT.getFirst();
                } else if (c > 0) {
                    // VBT > TMVBT, so TMVBT contains most recent update, this
                    // is a match!
                    // Next key is nextTMVBT.getFirst()
                    if (nextTMVBT.getFirst().compareTo(rangeMax) >= 0)
                        break;
                    if (!callback.callback(nextTMVBT)) {
                        // Stop indicated
                        return false;
                    }

                    // Mark current key for scanning to next entry
                    curKey = nextTMVBT.getFirst();

                    if (nextVBT == null) {
                        // No more entries in VBT, find the rest of the
                        // entries directly from the TMVBT
                        K nextStart = curKey.nextKey();
                        if (nextStart.equals(range.getMax()))
                            return true;
                        KeyRange<K> restRange = new KeyRangeImpl<K>(nextStart, range.getMax());
                        assert !restRange.isEmpty();
                        return tmvbt.getRange(restRange, callback, tx);
                    }
                } else {
                    // The same key found from VBT and TMVBT
                    // Mark current key for scanning to next entry
                    curKey = nextVBT.getFirst();
                    assert nextTMVBT.getFirst().equals(curKey);

                    UpdateMarker<V> marker = nextVBT.getSecond();
                    if (!marker.isDelete()) {
                        // Next key is nextVBT.getFirst()
                        if (nextVBT.getFirst().compareTo(rangeMax) >= 0)
                            break;
                        // VBT update is most recent for this key, and it is
                        // not a delete
                        if (!callback.callback(new Pair<K, V>(nextVBT.getFirst(), marker
                            .getValue()))) {
                            // Stop indicated
                            return false;
                        }
                    }
                }
            }

            // Broke out of loop, so we're past the entries.
            // No callback indicated stop, so return true
            return true;
        }
    }

    /**
     * Call this to start a maintenance transaction, if necessary.
     */
    public synchronized void runMaintenanceTransaction() {
        if (maintenanceTXRunning)
            return;

        log.debug("Starting maintenance TX");
        // Start maintenance TX
        maintenanceTXRunning = true;
        try {
            // Check if there is anything to do
            if (!cmvbt.hasTransientCommits()) {
                log.debug("No transient commits to move, returning");
                return;
            }

            int moveVer = cmvbt.getEarliestTransientCommitID();
            int startVer = cmvbt.getStartVersion(moveVer);

            CMVBTMaintenanceTX<K, V> maintenanceTX = new CMVBTMaintenanceTX<K, V>(cmvbt, tmvbt,
                vbt, moveVer, startVer);
            maintenanceTX.start();

        } finally {
            // Stop maintenance TX
            maintenanceTXRunning = false;
        }
    }

}
