package fi.hut.cs.treelib.mvbt;

import java.io.IOException;
import java.util.Set;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVDatabase;
import fi.hut.cs.treelib.OrderedTransaction;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.common.AbstractDatabase;
import fi.hut.cs.treelib.common.DatabaseConfigurationImpl;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.NonThrashingSMOPolicy;
import fi.hut.cs.treelib.common.OrderedTransactionImpl;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.hut.cs.treelib.common.VersionedKey;
import fi.hut.cs.treelib.concurrency.DefaultLatchManager;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;

/**
 * The database class for the concurrent multiversion B-tree.
 * 
 * @author thaapasa
 */
public class CMVBTDatabase<K extends Key<K>, V extends PageValue<?>> extends
    AbstractDatabase<K, V, MVBTPage<K, V>> implements MVDatabase<K, V, MVBTPage<K, V>> {

    private static final Logger log = Logger.getLogger(CMVBTDatabase.class);

    private static final PageID CMVBT_INFO_PAGE_ID = new PageID(1);
    private static final PageID TMVBT_INFO_PAGE_ID = new PageID(2);
    private static final PageID TMVBT_ROOTS_INFO_PAGE_ID = new PageID(3);
    private static final PageID VBT_INFO_PAGE_ID = new PageID(4);

    private TMVBTree<K, V> tmvbt;
    private VersionedBTree<K, V> vbt;

    private int lastMaintenanceRun = 0;
    private int maxMaintenanceAtOnce = 100;

    private PageBuffer vbtBuffer;
    private PageStorage vbtStorage;

    private final SMOPolicy vbtSMOPolicy;

    public CMVBTDatabase(int bufferSize, int vbtBufferSize, SMOPolicy vbtSMOPolicy,
        SMOPolicy tmvbtSMOPolicy, K keyPrototype, V valuePrototype, PageStorage pageStorage,
        PageStorage vbtPageStorage) {
        super(bufferSize - vbtBufferSize, pageStorage, tmvbtSMOPolicy, new DefaultLatchManager(),
            keyPrototype, valuePrototype);

        PageBuffer pageBuffer = getPageBuffer();

        this.vbtSMOPolicy = vbtSMOPolicy;
        vbtStorage = vbtPageStorage;
        vbtBuffer = (vbtStorage == pageStorage) ? pageBuffer : new PageBuffer(vbtStorage,
            vbtBufferSize, latchManager);

        initStructures();
    }
    
    public VersionedBTree<K, V> getVBT() {
        return vbt;
    }

    @Override
    protected void initStructures() {
        PageBuffer pageBuffer = getPageBuffer();
        pageBuffer.reservePageID(CMVBT_INFO_PAGE_ID);
        pageBuffer.reservePageID(TMVBT_INFO_PAGE_ID);
        pageBuffer.reservePageID(TMVBT_ROOTS_INFO_PAGE_ID);

        if (!vbtBuffer.isInitialized()) {
            vbtBuffer.initialize();
        }

        vbtBuffer.reservePageID(VBT_INFO_PAGE_ID);

        // Create root* for the TMVBT
        BTree<IntegerKey, PageID> rootTree = new BTree<IntegerKey, PageID>("roots", "Root*",
            TMVBT_ROOTS_INFO_PAGE_ID, new DatabaseConfigurationImpl<IntegerKey, PageID>(this,
                IntegerKey.PROTOTYPE, PageID.PROTOTYPE, new NonThrashingSMOPolicy(0.2, 0.2)));

        // Create the TMVBT
        tmvbt = new TMVBTree<K, V>(TMVBT_INFO_PAGE_ID, rootTree, this);
        tmvbt.setCheckFixes(false);

        // Create the VBT
        vbt = new VersionedBTree<K, V>(0.3, VBT_INFO_PAGE_ID, getVBTConfig());
        vbt.setCheckFixes(false);

        // Set VBT to auto-overwrite entries
        vbt.setOverwriteEntries(true);
        // Create the CMVBT wrapper
        initialize(new CMVBTree<K, V>(CMVBT_INFO_PAGE_ID, tmvbt, vbt, this));
    }

    @Override
    protected void clearStructures() {
        tmvbt.close();
        tmvbt = null;

        // Close VBT page buffer (if it's different)
        if (vbtBuffer != pageBuffer) {
            try {
                // Closing the page buffer closes the page storage also
                vbtBuffer.close();
            } catch (IOException e) {
                log.error("Error when closing database: " + e, e);
            }
            vbtBuffer.clearStructures();
        }

        vbt.close();
        vbt = null;
    }

    private DatabaseConfigurationImpl<VersionedKey<K>, UpdateMarker<V>> getVBTConfig() {
        return new DatabaseConfigurationImpl<VersionedKey<K>, UpdateMarker<V>>(this,
            new VersionedKey<K>(keyPrototype, 1), UpdateMarker.createInsert(valuePrototype),
            vbtSMOPolicy) {
            @Override
            public PageBuffer getPageBuffer() {
                return vbtBuffer;
            }

            @Override
            public PageStorage getPageStorage() {
                return vbtStorage;
            }
        };
    }

    public void setAutoMaintenance(int frequency, int numAtOnce) {
        Configuration.instance().setMaintenanceFrequency(frequency);
        this.maxMaintenanceAtOnce = numAtOnce;
    }

    @Override
    public OrderedTransaction<K, V> beginTransaction() {
        lastMaintenanceRun++;
        int maxVer = getDatabaseTree().getCommittedVersion();
        getStatisticsLogger().log(GlobalOperation.GO_NEW_TRANSACTION);
        return new OrderedTransactionImpl<K, V, MVBTPage<K, V>>(this, maxVer, false);
    }

    @Override
    public OrderedTransaction<K, V> beginReadTransaction(int version) {
        lastMaintenanceRun++;
        getStatisticsLogger().log(GlobalOperation.GO_NEW_TRANSACTION);
        return new OrderedTransactionImpl<K, V, MVBTPage<K, V>>(this, version, true);
    }

    @Override
    public int commit(Transaction<K, V> tx) {
        int commitVer = 0;
        if (tx.isReadOnly()) {
            // Read-only transactions require no special actions
            commitVer = tx.getReadVersion();
        } else {
            commitVer = getDatabaseTree().commitTransaction(tx);
            log.debug(String.format("Committed transaction %d to final commit-version %d", tx
                .getTransactionID(), commitVer));
        }
        // Check if maintenance is required
        if (lastMaintenanceRun >= Configuration.instance().getMaintenanceFrequency()) {
            requestMaintenance();
        }
        return commitVer;
    }

    @Override
    public int getCommittedVersion() {
        return getDatabaseTree().getCommittedVersion();
    }

    @Override
    public String toString() {
        return String.format("CMVBT database (m = %d), buffers: %d, %d", Configuration.instance()
            .getMaintenanceFrequency(), getPageBuffer().getBufferSize(), vbt.getPageBuffer()
            .getBufferSize());
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
        getDatabaseTree().printDebugInfo();
        getPageBuffer().printDebugInfo();
    }

    @Override
    public CMVBTree<K, V> getDatabaseTree() {
        return (CMVBTree<K, V>) super.getDatabaseTree();
    }

    @Override
    public CMVBTDatabase<K, V> getMVDatabase() {
        return this;
    }

    @Override
    public Set<Integer> getSeparateRootedVersions() {
        Set<Integer> versions = tmvbt.getSeparateRootedVersions();
        versions.add(Integer.MAX_VALUE);
        return versions;
    }

    // private static boolean LOG_VBT_SIZE = false;
    // private long maxVBTEntries = 0;
    // private long maxVBTPages = 0;
    //
    // public void printStat() {
    // System.out.println("Max VBT entries: " + maxVBTEntries);
    // System.out.println("Max VBT pages: " + maxVBTPages);
    // }

    @Override
    public void requestMaintenance() {
        super.requestMaintenance();
        log.debug("Requesting maintenance transaction on the CMVBT");
        int c = 0;
        // if (LOG_VBT_SIZE && getDatabaseTree().needMaintenance()) {
        // long curMVBTPages = TreeShortcuts.countPages(vbt, false);
        // maxVBTPages = Math.max(maxVBTPages, curMVBTPages);
        // maxVBTEntries = Math.max(maxVBTEntries,
        // TreeShortcuts.countEntries(vbt));
        // if (curMVBTPages > 1) {
        // BTreePage<?, ?> p = vbt.getRoot(vbt.internalOwner);
        // // System.out.println("VBT root: " + p);
        // vbt.getPageBuffer().unfix(p, vbt.internalOwner);
        //
        // p = vbt.getPage(vbt.getKeyPrototype().getMinKey(),
        // vbt.internalOwner);
        // // System.out.println("VBT leaf: " + p);
        // vbt.getPageBuffer().unfix(p, vbt.internalOwner);
        // }
        // }
        while (getDatabaseTree().needMaintenance() && c++ < maxMaintenanceAtOnce) {
            getDatabaseTree().runMaintenanceTransaction();
        }
        if (!getDatabaseTree().needMaintenance()) {
            lastMaintenanceRun = 0;
        }
    }

    @Override
    public boolean isMultiDimension() {
        return false;
    }

    @Override
    public boolean isMultiVersion() {
        return true;
    }

}
