package fi.hut.cs.treelib.mvbt;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.btree.BTreePage;
import fi.hut.cs.treelib.common.AbstractTree;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.hut.cs.treelib.common.VersionedKey;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.Pair;

public class CMVBTMaintenanceTX<K extends Key<K>, V extends PageValue<?>> {

    private static final Logger log = Logger.getLogger(CMVBTMaintenanceTX.class);

    private final CMVBTree<K, V> cmvbt;
    private final TMVBTree<K, V> tmvbt;
    private final VersionedBTree<K, V> vbt;
    private final int moveVer;
    private final int startVer;

    private static final Owner MAINTENANCE_OWNER = new OwnerImpl("CMVBT-maintenance");

    protected CMVBTMaintenanceTX(CMVBTree<K, V> cmvbt, TMVBTree<K, V> tmvbt,
        VersionedBTree<K, V> vbt, int moveVer, int startVer) {
        this.cmvbt = cmvbt;
        this.tmvbt = tmvbt;
        this.vbt = vbt;
        this.moveVer = moveVer;
        this.startVer = startVer;
    }

    public void start() {
        cmvbt.getStatisticsLogger().log(GlobalOperation.GO_MAINTENANCE_TX);
        cmvbt.getStatisticsLogger().log(Operation.OP_MAINTENANCE_TX);
        log.debug("Starting maintenance transaction; moving " + startVer + " -> " + moveVer);
        // Step 1. Move version counter, find transient start time version
        // Already done

        // Find the mapping used
        CMVBTree<K, V>.TransientMapping mapping = cmvbt.getTransientMappingForReadTX(moveVer);
        // Consistency check for the mapping...
        assert mapping.startVersions.size() == 1;
        assert mapping.startVersions.contains(startVer);
        assert mapping.stc.get(startVer) == moveVer;

        // Step 2. Apply all updates of version moveVer to the TMVBT
        applyUpdatesToTMVBT(mapping);

        // Step 3. Increase stable version counter
        cmvbt.setStableVersion(moveVer, MAINTENANCE_OWNER);

        // Step 4. Delete updates from the vbt
        deleteUpdatesFromVBT(mapping);

        // Step 5. Remove mapping moveVer -> startVer from the CTS table
        cmvbt.removeTransientMapping(moveVer, startVer);
    }

    /**
     * Finds all updates of transient start version startVer from the VBT, and
     * applies them to the TMVBT with version number moveVer.
     */
    private void applyUpdatesToTMVBT(CMVBTree<K, V>.TransientMapping mapping) {
        log.debug("Starting to apply updates to the TMVBT");

        K curKey = cmvbt.getKeyPrototype().getMinKey();
        // VBT can be scanned using leaf-page links (no full path needed)
        PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>> vbtPath = new PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>>(
            false);

        int tmvbtTXID = tmvbt.beginTransaction(MAINTENANCE_OWNER);
        assert tmvbtTXID == moveVer : tmvbtTXID + " != " + moveVer;

        Transaction<VersionedKey<K>, UpdateMarker<V>> vbtTX = new DummyTransaction<VersionedKey<K>, UpdateMarker<V>>(
            MAINTENANCE_OWNER);
        Transaction<K, V> tmvbtTX = new DummyTransaction<K, V>(moveVer, moveVer,
            MAINTENANCE_OWNER);

        PagePath<K, V, MVBTPage<K, V>> mvbtPath = new PagePath<K, V, MVBTPage<K, V>>(true);
        while (true) {
            Pair<K, UpdateMarker<V>> entry = vbt.findNextMarker(curKey, mapping, vbtPath, vbtTX);
            if (entry == null) {
                // No more updates
                break;
            }
            curKey = entry.getFirst();
            UpdateMarker<V> update = entry.getSecond();

            log.debug("Applying update " + update + " to TMVBT");

            if (update.isDelete()) {
                tmvbt.delete(curKey, mvbtPath, tmvbtTX);
            } else {
                tmvbt.insert(curKey, update.getValue(), mvbtPath, tmvbtTX);
            }
        }
        vbt.getPageBuffer().unfix(vbtPath, tmvbtTX);
        tmvbt.getPageBuffer().unfix(mvbtPath, tmvbtTX);

        tmvbt.commitTransaction(tmvbtTX);
        // Updated!
    }

    /**
     * Deletes all updates of transient start version startVer from the VBT.
     */
    private void deleteUpdatesFromVBT(CMVBTree<K, V>.TransientMapping mapping) {
        log.debug("Starting to delete updates from the VBT");
        int startVer = mapping.firstStart;

        K curKey = cmvbt.getKeyPrototype().getMinKey();
        PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>> vbtPath = new PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>>(
            true);

        Transaction<VersionedKey<K>, UpdateMarker<V>> tx = new DummyTransaction<VersionedKey<K>, UpdateMarker<V>>(
            AbstractTree.DEFAULT_READ_VERSION, AbstractTree.DEFAULT_TRANSACTION_ID,
            MAINTENANCE_OWNER);
        while (true) {
            Pair<K, UpdateMarker<V>> entry = vbt.findNextMarker(curKey, mapping, vbtPath, tx);
            if (entry == null) {
                // No more updates
                break;
            }

            VersionedKey<K> key = new VersionedKey<K>(entry.getFirst(), startVer);
            vbt.delete(key, vbtPath, tx);
            curKey = entry.getFirst();
        }

        vbt.getPageBuffer().unfix(vbtPath, tx);
    }

}
