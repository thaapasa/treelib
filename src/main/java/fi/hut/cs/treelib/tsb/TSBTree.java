package fi.hut.cs.treelib.tsb;

import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.OrderedTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.TransactionIDManager;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.VisualizableTree;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.btree.BTreePage;
import fi.hut.cs.treelib.common.AbstractMVTree;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.TreeShortcuts;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.hut.cs.treelib.storage.PageFlushListener;
import fi.hut.cs.treelib.storage.StoredPage;
import fi.hut.cs.treelib.tsb.TSBOperations.SplitPolicy;
import fi.hut.cs.treelib.util.MVKeyRangePredicate;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.Holder;
import fi.tuska.util.NotImplementedException;
import fi.tuska.util.Pair;
import fi.tuska.util.Triple;

public class TSBTree<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMVTree<K, V, TSBPage<K, V>> implements MVTree<K, V, TSBPage<K, V>>,
    OrderedTree<K, V, TSBPage<K, V>>, VisualizableTree<K, V, TSBPage<K, V>>, PageFlushListener,
    TransactionIDManager<K> {

    private static final Logger log = Logger.getLogger(TSBTree.class);

    protected static final Integer UNCOMMITTED_ID = -1;
    private TSBOperations<K, V> operations;

    /**
     * Greatest version v such that all versions up to and including v are
     * committed.
     */
    private int committedVersion = 0;

    /** Current root node. Is kept fixed at all times. */
    private TSBPage<K, V> root;

    private final PageFactory<TSBInfoPage<K>> infoPageFactory;

    /**
     * The range of key version space in the database, for visualization. Will
     * not shrink, only expand.
     */
    private MVKeyRange<K> range;

    /**
     * Volatile timestamp table (VTT): mapping from [temporary transaction id]
     * to [commit-time, ref count, flushed to disk].
     * 
     * <p>
     * This table is used for lazy timestamping. At various situations, the
     * temporary IDs in a page will be checked from the VTT, and corrected to
     * match the actual commit-time versions.
     * 
     * <p>
     * The situations when this timestamping is applied are:
     * 
     * <ul>
     * <li>Key update: all versions of the key are checked and corrected
     * <li>Page flush: when page is flushed to disk, all entries are checked
     * <li>Key read: all versions of the key are checked and corrected
     * <li>Page split: all entries are checked
     * </ul>
     */
    private Map<Integer, Triple<Integer, Integer, Boolean>> vtt = new TreeMap<Integer, Triple<Integer, Integer, Boolean>>();

    /**
     * Persistent timetamp table (PTT): mapping from [temporary transaction
     * id] to [commit-time]
     */
    private BTree<IntegerKey, IntegerValue> ptt;
    private Transaction<IntegerKey, IntegerValue> pttTX = new DummyTransaction<IntegerKey, IntegerValue>(
        "PTT");

    private Deque<IntegerKey> pttDeleteList = new LinkedList<IntegerKey>();

    protected final boolean batchPTTUpdate;

    protected TSBTree(PageID infoPageID, SplitPolicy splitPolicy, boolean batchPTTUpdate,
        BTree<IntegerKey, IntegerValue> ptt, DatabaseConfiguration<K, V> dbConfig) {
        super("tsb", "Time-Split B-tree", infoPageID, dbConfig);

        this.batchPTTUpdate = batchPTTUpdate;
        this.ptt = ptt;
        this.ptt.setOverwriteEntries(true);
        this.range = new MVKeyRange<K>(keyPrototype.fromInt(0), 0, 1);
        this.infoPageFactory = new TSBInfoPage<K>(dbConfig.getPageSize(), range);
        PageBuffer.registerPageFactory(infoPageFactory);

        initialize(new TSBPageFactory<K, V>(this, dbConfig.getPageSize()));

        // Must be after pageBuffer is initialized and pageFactory has been
        // created
        this.operations = new TSBOperations<K, V>(this, splitPolicy);
        loadTree();

        setDefaultBufferFixesAfterActions(2);

        assert getPageBuffer() == ptt.getPageBuffer();
    }

    protected synchronized void beginTransaction(Transaction<K, V> tx) {
        if (tx.isUpdating()) {
            // Previously, we added an "uncommitted" dummy entry to
            // PTT when a transaction begins. This does not need to be done,
            // the PTT is updated only after a transaction commits (or as a
            // batch run when PTT batch updating is selected.

            // TreeShortcuts.insert(ptt, new
            // IntegerKey(tx.getTransactionID()), new
            // IntegerValue(UNCOMMITTED_ID), pttTX);
            vtt.put(tx.getTransactionID(), new Triple<Integer, Integer, Boolean>(null, 0,
                Boolean.FALSE));
            if (log.isDebugEnabled())
                log.debug("Begin new " + tx.getDebugID());
        }
        updateInfoPage(tx);
    }

    protected void setSplitPolicy(SplitPolicy policy) {
        log.info("Setting TSB-tree split policy to " + policy);
        operations.setSplitPolicy(policy);
    }

    protected synchronized int commitTransaction(Transaction<K, V> tx) {
        committedVersion++;
        range = range.extendVersion(committedVersion);
        if (tx.isUpdating()) {
            Pair<Integer, Integer> cur = vtt.get(tx.getTransactionID());
            Integer refCount = cur != null ? cur.getSecond() : null;

            if (log.isDebugEnabled())
                log.debug("Commit " + tx.getDebugID() + " (refs: " + refCount + ") to cver "
                    + committedVersion + ", marking this to PTT and VTT");

            if (!batchPTTUpdate) {
                // Update PTT unless PTT batch updating is selected.
                // In PTT batch updating, the PTT is updated when maintenance
                // is requested.
                TreeShortcuts.insert(ptt, new IntegerKey(tx.getTransactionID()),
                    new IntegerValue(committedVersion), pttTX);
            }
            // VTT is updated anyway
            vtt.put(tx.getTransactionID(), new Triple<Integer, Integer, Boolean>(
                committedVersion, refCount, !batchPTTUpdate));
        }
        updateInfoPage(tx);
        return committedVersion;
    }

    protected void flushVTTtoPTT() {
        if (!batchPTTUpdate) {
            // No batch PTT updating, so do nothing
            return;
        }
        log.info(String
            .format("Flushing VTT entries to PTT, VTT contains %d entries", vtt.size()));

        // Batch-insert VTT entries to PTT
        PagePath<IntegerKey, IntegerValue, BTreePage<IntegerKey, IntegerValue>> pttPath = new PagePath<IntegerKey, IntegerValue, BTreePage<IntegerKey, IntegerValue>>(
            true);

        int flushed = 0;
        for (Entry<Integer, Triple<Integer, Integer, Boolean>> entry : vtt.entrySet()) {
            // vtt is a TreeMap so the entries are iterated in ascending key
            // order

            Triple<Integer, Integer, Boolean> info = entry.getValue();
            if (!info.getThird()) {
                // This is not yet persisted to PTT, so flush it
                ptt.insert(new IntegerKey(entry.getKey()), new IntegerValue(info.getFirst()),
                    pttPath, pttTX);

                // Update the VTT entry
                info.setThird(Boolean.TRUE);
                flushed++;
            }
        }
        pageBuffer.unfix(pttPath, pttTX);
        log.info(String.format("VTT flush complete, flushed %d entries", flushed));

    }

    @Override
    public int getHeight() {
        return root != null ? root.getHeight() : 0;
    }

    @Override
    public int getHeight(int version) {
        return getHeight();
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public PageID getRootPageID(int version) {
        if (root == null)
            return null;
        return root.getPageID();
    }

    @Override
    public TSBOperations<K, V> getOperations() {
        return operations;
    }

    /**
     * @return a fixed root page
     */
    @Override
    public TSBPage<K, V> getRoot(int version, Owner owner) {
        return getRoot(owner);
    }

    @Override
    public PageID getRootPageID() {
        return (root != null) ? root.getPageID() : null;
    }

    @Override
    public TSBPage<K, V> createIndexRoot(int height, Transaction<K, V> tx) {
        assert root != null;
        int curRootStartVer = root.getKeyRange().getMinVersion();

        TSBPage<K, V> page = super.createIndexRoot(height, tx);
        // Root always has version range starting from the original root start
        // ver
        page.setKeyRange(page.getKeyRange().startVersionRange(curRootStartVer));
        return page;
    }

    @Override
    public TSBPage<K, V> createSiblingPage(TSBPage<K, V> page, Owner owner) {
        TSBPage<K, V> sibling = super.createSiblingPage(page, owner);
        sibling.setKeyRange(sibling.getKeyRange().startVersionRange(
            page.getKeyRange().getMinVersion()));
        return sibling;
    }

    @Override
    public TSBPage<K, V> createLeafRoot(Transaction<K, V> tx) {
        TSBPage<K, V> page = super.createLeafRoot(tx);
        page.setKeyRange(page.getKeyRange().startVersionRange(tx.getReadVersion()));
        return page;
    }

    /**
     * @return a fixed root page
     */
    @Override
    public TSBPage<K, V> getRoot(Owner owner) {
        return root != null ? pageBuffer.fixPage(root.getPageID(), pageFactory, false, owner)
            : null;
    }

    @Override
    public Tree<K, V, TSBPage<K, V>> getVersionTree(int version) {
        return this;
    }

    @Override
    public int getCommittedVersion() {
        return committedVersion;
    }

    @Override
    public MVKeyRange<K> getKeyRange() {
        return range;
    }

    @Override
    public boolean isEmpty(final Transaction<K, V> tx) {
        if (root == null)
            return true;
        // found means that an alive entry has been found
        final Holder<Boolean> found = new Holder<Boolean>(false);
        // Must check contents...
        MVKeyRangePredicate<K> pred = new MVKeyRangePredicate<K>(keyPrototype);
        pred.setRestrictedVersionRange(KeyRangeImpl.getKeyRange(new IntegerKey(tx
            .getReadVersion())));
        pred.setOnlyHeight(1);

        root.traverseMVPages(pred, new Callback<Page<K, V>>() {
            @Override
            public boolean callback(Page<K, V> page) {
                TSBPage<K, V> tPage = (TSBPage<K, V>) page;
                if (tPage.getAliveEntryCount(tx) > 0) {
                    found.setValue(true);
                    // Don't continue search
                    return false;
                }
                // Continue search
                return true;
            }
        }, tx);
        return !found.getValue();
    }

    @Override
    public int getMaxHeight() {
        // In TSB, the height is the max height (TSB is a tree)
        return getHeight();
    }

    @Override
    public Collection<VisualizablePage<K, V>> getPagesAtHeight(int height) {
        SortedSet<VisualizablePage<K, V>> pages = new TreeSet<VisualizablePage<K, V>>();
        if (root != null) {
            root.collectPagesAtHeight(height, pages, internalOwner);
        }
        return pages;
    }

    @Override
    public void attachRoot(TSBPage<K, V> newRoot, Transaction<K, V> tx) {
        if (root != null && root.equals(newRoot)) {
            // Already attached
            log.debug("Page " + newRoot + " already attached, not reattaching");
            return;
        }
        log.debug(String.format("Attaching new root %s to tree by transaction %d", newRoot
            .getName(), tx.getTransactionID()));

        if (root != null) {
            // Unfix old root
            pageBuffer.unfix(root, tx);
        }

        root = pageBuffer.fixPage(newRoot.getPageID(), pageFactory, false, tx);
        assert root != null;
        updateInfoPage(tx);
    }

    @Override
    protected void updateInfoPage(Owner owner) {
        TSBInfoPage<K> infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, false, owner);
        infoPage.setRootPageID(root != null ? root.getPageID() : PageID.INVALID_PAGE_ID);
        infoPage.setCommittedVersion(committedVersion);
        infoPage.setKeyRange(range);
        infoPage.setLastOwnerID(OwnerImpl.getLastUsedID());
        pageBuffer.unfix(infoPage, owner);
    }

    @Override
    protected void loadTree() {
        if (pageBuffer.reservePageID(infoPageID)) {
            // The info page was not reserved, so this is a new DB
            log.info("Initializing new TSB DB information page");
        } else {
            log.info("Loading an existing TSB DB information page");
        }
        TSBInfoPage<K> infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, true,
            internalOwner);

        if (infoPage.getRootPageID().intValue() > 0) {
            // Load root page
            root = pageBuffer
                .fixPage(infoPage.getRootPageID(), pageFactory, false, internalOwner);
            assert root != null;
        }
        this.committedVersion = infoPage.getCommittedVersion();
        this.range = infoPage.getKeyRange();
        int ownerID = infoPage.getLastOwnerID();
        OwnerImpl.resetID(ownerID);

        pageBuffer.unfix(infoPage, internalOwner);
    }

    @Override
    public String toString() {
        return String.format("TSB, committed version %d, root: %s", getCommittedVersion(),
            root != null ? root : "none");
    }

    @Override
    public void clearInternal(Transaction<K, V> tx) {
        throw new UnsupportedOperationException("Not used in TSB");
    }

    /**
     * ACTION: insert
     */
    @Override
    public boolean insert(K key, V value, PagePath<K, V, TSBPage<K, V>> savedPath,
        Transaction<K, V> tx) {
        assert savedPath != null;
        if (root == null) {
            // Create new root for tree
            TSBPage<K, V> newRoot = createLeafRoot(tx);
            pageBuffer.unfix(newRoot, tx);
        }
        assert root != null;

        savedPath = operations.validatePathToLeafPage(root.getPageID(), key, savedPath, tx);

        boolean success = operations.insert(savedPath, key, value, tx);

        if (success) {
            range = range.extend(key);
        }
        return success;
    }

    @Override
    public boolean delete(K key, PagePath<K, V, TSBPage<K, V>> savedPath, Transaction<K, V> tx) {
        assert savedPath != null;
        if (root == null)
            return false;

        savedPath = operations.validatePathToLeafPage(root.getPageID(), key, savedPath, tx);
        assert savedPath.isMaintainFullPath();

        V value = operations.delete(savedPath, key, tx);
        if (Configuration.instance().isCheckConsistency()) {
            checkConsistency();
        }
        return value != null;
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
    }

    @Override
    public void traverseMVPages(Predicate<MVKeyRange<K>> predicate,
        Callback<Page<K, V>> operation, Owner owner) {
        TSBPage<K, V> r = getRoot(owner);
        if (r == null)
            return;
        r.traverseMVPages(predicate, operation, owner);
        pageBuffer.unfix(r, owner);
    }

    @Override
    public boolean isMultiVersion() {
        return true;
    }

    /**
     * Called from PageBuffer just before the given page is flushed to disk.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void prepareForFlush(StoredPage storedPage) {
        if (storedPage instanceof TSBPage) {
            TSBPage<K, V> page = (TSBPage<K, V>) storedPage;
            page.updateCommittedTimestamps();
        }
    }

    /**
     * @return the VTT entry for this transaction, creating it if it can't be
     * found
     */
    private Triple<Integer, Integer, Boolean> getVTTEntry(int transactionID) {
        // First look for the entry in VTT
        Triple<Integer, Integer, Boolean> info = vtt.get(transactionID);
        // If this pair is not found, then DB has been loaded from disk and
        // the temporary information is still only in PTT
        if (info == null) {
            // Load the version information from PTT
            IntegerValue commitVer = ptt.get(new IntegerKey(transactionID), pttTX);
            if (commitVer == null) {
                // No information in PTT; therefore this TX is not yet
                // committed
                info = new Triple<Integer, Integer, Boolean>(null, null, Boolean.FALSE);
                throw new RuntimeException("No info in PTT for " + transactionID);
            } else {
                // TX is committed with commit time commitVer
                info = new Triple<Integer, Integer, Boolean>(commitVer.intValue(), null,
                    Boolean.TRUE);
            }
            // When loading from PTT, the ref count is set to null (cannot be
            // tracked anymore)
            vtt.put(transactionID, info);
        }
        return info;
    }

    @Override
    public Integer getCommitVersion(int transactionID) {
        // First look for the entry in VTT
        Pair<Integer, Integer> info = getVTTEntry(transactionID);
        assert info != null;

        // First item in the info pair is the commit version; if it is not
        // null and not UNCOMMITTED_ID, then this TX is committed
        Integer cVer = info.getFirst();
        return cVer != null && !cVer.equals(UNCOMMITTED_ID) ? cVer : null;
    }

    @Override
    public void notifyTempConverted(K key, int transactionID, int commitTime) {
        // Find the corresponding VTT entry
        Triple<Integer, Integer, Boolean> info = getVTTEntry(transactionID);
        // This must exist because getVTTEntry() creates it if missing
        assert info != null;
        // If ref count is null, then it cannot be tracked
        if (info.getSecond() == null)
            return;

        assert info.getSecond() > 0 : "Too many temporaries converted (ref-count already zero): "
            + info + " for key " + key + " when converting " + transactionID + " to "
            + commitTime;
        int newCount = info.getSecond() - 1;
        info.setSecond(newCount);

        if (log.isDebugEnabled())
            log.debug("Temp at TX:" + transactionID + ":C:" + commitTime + ": " + key
                + " converted to commit-time, new count is " + newCount);

        if (newCount < 1) {
            if (log.isDebugEnabled())
                log.debug("Transaction TX:" + transactionID + ":C:" + commitTime
                    + " has been fully converted, removing entries");
            // We can remove this entry, because all the references have been
            // removed
            vtt.remove(transactionID);
            // Remove entry from PTT also
            // Cannot delete right away, because the call to this method might
            // have been caused by a page flush operation (not a nice loop).
            // So, mark up information about to-be-deleted-ptt-entries
            if (info.getThird()) {
                // Need to delete the entry only if it is persisted
                synchronized (pttDeleteList) {
                    pttDeleteList.addLast(new IntegerKey(transactionID));
                }
            } else {
                assert batchPTTUpdate;
            }
        }
    }

    /**
     * Delete entries from the PTT. These entries have became unused (all TX
     * ids converted properly), but the PTT entry could not have been
     * converted before.
     */
    private void deleteQueuedPTTEntries() {
        synchronized (pttDeleteList) {
            PagePath<IntegerKey, IntegerValue, BTreePage<IntegerKey, IntegerValue>> path = new PagePath<IntegerKey, IntegerValue, BTreePage<IntegerKey, IntegerValue>>(
                true);
            while (!pttDeleteList.isEmpty()) {
                IntegerKey k = pttDeleteList.removeFirst();
                ptt.delete(k, path, pttTX);
            }
            ptt.getPageBuffer().unfix(path, pttTX);
        }
    }

    @Override
    public void runAfterAction() {
        super.runAfterAction();
        deleteQueuedPTTEntries();
    }

    @Override
    public void notifyTempInserted(K key, int transactionID) {
        // Find the corresponding VTT entry
        Pair<Integer, Integer> info = getVTTEntry(transactionID);
        // This must exist because getVTTEntry() creates it if missing
        assert info != null;
        // If ref count is null, then it cannot be tracked
        if (info.getSecond() == null)
            return;

        int newCount = info.getSecond() + 1;
        info.setSecond(newCount);
        if (log.isDebugEnabled())
            log.debug("New temp for TX:" + transactionID + ": " + key + "; count is " + newCount);
    }

    @Override
    public void setStatisticsLogger(StatisticsLogger stats) {
        super.setStatisticsLogger(stats);
        ptt.setStatisticsLogger(stats);
    }

    protected Map<Integer, Triple<Integer, Integer, Boolean>> getVTT() {
        return vtt;
    }

    protected BTree<IntegerKey, IntegerValue> getPTT() {
        return ptt;
    }

    @Override
    public Pair<K, V> floorEntry(K key, Transaction<K, V> tx) {
        throw new NotImplementedException();
    }

    @Override
    public Pair<K, V> nextEntry(K key, PagePath<K, V, TSBPage<K, V>> savedPath,
        Transaction<K, V> tx) {
        // Simple implementation: find next entry using range query
        return TreeShortcuts.nextEntry(this, key, tx);
    }

    @Override
    public String getIdentifier() {
        return super.getIdentifier() + "-" + operations.getSplitPolicy().toString().toLowerCase();
    }

    @Override
    public void flush() {
        // Flush VTT to PTT
        flushVTTtoPTT();
        // Continue with normal flush
        super.flush();
    }

}
