package fi.hut.cs.treelib.mvbt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.MVTreeOperations;
import fi.hut.cs.treelib.OrderedTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.common.AbstractMVTree;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.SingleVersionTree;
import fi.hut.cs.treelib.common.TreeShortcuts;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.Holder;
import fi.tuska.util.NotImplementedException;
import fi.tuska.util.Pair;

/**
 * Saved path given in the parameters of this class is not actually used right
 * now, because the underlying structure actually uses two saved paths (one
 * for the TMVBT and one for the VBT). We could implement a saved path
 * subclass that stores two saved paths to deal with this issue.
 * 
 * @author thaapasa
 */
public class CMVBTree<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMVTree<K, V, MVBTPage<K, V>> implements MVTree<K, V, MVBTPage<K, V>>,
    OrderedTree<K, V, MVBTPage<K, V>>, Component {

    private static final Logger log = Logger.getLogger(CMVBTree.class);

    private int maxCommittedTransactionID;
    /** Stable version. Same as tmvbt.getCommittedVersion(). */
    private int stableVer;

    /** Commit-to-start version mapping */
    private SortedMap<Integer, Integer> cts = new TreeMap<Integer, Integer>();

    /** The transactional MVBT storage for stable transactions */
    private TMVBTree<K, V> tmvbt;
    /** The temporary versioned B-tree for transient transactions */
    private VersionedBTree<K, V> vbt;

    private CMVBTOperations<K, V> operations;

    private PageFactory<CMVBTInfoPage<K>> infoPageFactory;
    private MVKeyRange<K> range;

    protected CMVBTree(PageID infoPageID, TMVBTree<K, V> tmvbt, VersionedBTree<K, V> vbt,
        DatabaseConfiguration<K, V> dbConfig) {
        super("cmvbt", "CMVBT", infoPageID, dbConfig);

        this.vbt = vbt;
        this.tmvbt = tmvbt;

        this.vbt.setCheckFixes(false);
        this.tmvbt.setCheckFixes(false);
        setCheckFixes(false);

        // Currently, a default range is forced. This could be changed, but is
        // it really worth it?
        this.range = new MVKeyRange<K>(keyPrototype.fromInt(0), 1, 2);

        this.infoPageFactory = new CMVBTInfoPage<K>(dbConfig.getPageSize(), this.range);
        PageBuffer.registerPageFactory(infoPageFactory);

        initialize(tmvbt.getPageFactory());

        this.operations = new CMVBTOperations<K, V>(this, this.tmvbt, this.vbt);

        loadTree();
    }

    public boolean hasTransientCommits() {
        return !cts.isEmpty();
    }

    public int getEarliestTransientCommitID() {
        return cts.firstKey();
    }

    @Override
    public String getIdentifier() {
        return super.getIdentifier() + "-" + Configuration.instance().getMaintenanceFrequency();
    }

    public int getStartVersion(int commitVersion) {
        Integer cv = cts.get(commitVersion);
        assert cv != null;
        return cv;
    }

    /**
     * Called from the maintenance transaction to increase the stable version
     * counter.
     */
    protected void setStableVersion(int commitVer, Owner owner) {
        // Old stable version must be lower than the new commit version
        assert stableVer < commitVer : stableVer + " >= " + commitVer;
        // Check that CTS and the commitVer match
        assert cts.firstKey() == commitVer : cts.firstKey() + " != " + commitVer;
        stableVer = commitVer;
        updateInfoPage(owner);
    }

    /**
     * Called from the maintenance transaction to remove the transient mapping
     * from the CTS table.
     */
    protected void removeTransientMapping(int commitVer, int startVer) {
        // Check that the maintenance TX only tries to delete the first key
        // from CTS
        assert cts.firstKey() == commitVer : cts.firstKey() + " != " + commitVer;
        Integer oldValue = cts.remove(commitVer);
        assert oldValue == startVer : oldValue + " != " + startVer;
    }

    public int getCommittedVersion() {
        return maxCommittedTransactionID;
    }

    @Override
    public void flush() {
        // First flush all transient commits into the TMVBT
        while (hasTransientCommits()) {
            runMaintenanceTransaction();
        }

        // Then do standard DB flush
        super.flush();
    }

    /**
     * @param transactionID the temporary start-time version number of the
     * transaction
     * @return the commit-time version number of the transaction
     */
    protected int commitTransaction(Transaction<K, V> tx) {
        // Increase the max committed TX ID counter
        int commitVer = ++maxCommittedTransactionID;
        cts.put(commitVer, tx.getTransactionID());
        updateInfoPage(tx);
        return commitVer;
    }

    protected class TransientMapping {
        /** Reverse mapping from start-time versions to commit-time versions */
        public Map<Integer, Integer> stc = new HashMap<Integer, Integer>();
        /** List of start-time versions, ordered by the starting time */
        public Set<Integer> startVersions = new HashSet<Integer>();

        public Integer firstStart = null;
        public Integer lastStart = null;

        public TransientMapping(int version) {
            if (version > stableVer) {
                // Loop through transient commit-time versions
                for (int v = stableVer + 1; v <= version; v++) {
                    // Find transient start ver from CTS table
                    Integer startV = cts.get(v);
                    // If no entry found from CTS, assume that transaction v
                    // is a read-only transaction with no updates
                    if (startV != null) {
                        addMapping(startV, v);
                    }
                }
            }
        }

        public TransientMapping(int version, int transientID) {
            this(version);
            // Check the version itself (for active transactions)
            if (!stc.containsKey(transientID)) {
                assert !startVersions.contains(transientID);
                // Not committed, make this version the most important one
                addMapping(transientID, Integer.MAX_VALUE);
            } else {
                assert startVersions.contains(transientID);
            }
        }

        public void addMapping(int startTime, int commitTime) {
            stc.put(startTime, commitTime);
            startVersions.add(startTime);
            if (firstStart == null || startTime < firstStart)
                firstStart = startTime;
            if (lastStart == null || startTime > lastStart)
                lastStart = startTime;
        }

    }

    protected TransientMapping getTransientMappingForReadTX(int version) {
        return new TransientMapping(version);
    }

    protected TransientMapping getTransientMappingForUpdatingTX(int version, int transientID) {
        return new TransientMapping(version, transientID);
    }

    @Override
    public int getHeight() {
        return tmvbt.getHeight();
    }

    @Override
    public int getMaxHeight() {
        return tmvbt.getMaxHeight();
    }

    @Override
    public boolean isEmpty(Transaction<K, V> tx) {
        // Is the tree empty? Assume that it is.
        final Holder<Boolean> empty = new Holder<Boolean>(Boolean.TRUE);
        getRange(keyPrototype.getEntireRange(), new Callback<Pair<K, V>>() {
            @Override
            public boolean callback(Pair<K, V> object) {
                // Tree is not empty!
                empty.setValue(Boolean.FALSE);
                // Return false: no need to continue
                return false;
            }
        }, tx);
        return empty.getValue();
    }

    @Override
    public boolean isMultiVersion() {
        return true;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    public boolean isStable(int commitTimeVersion) {
        return commitTimeVersion <= stableVer;
    }

    @Override
    protected void loadTree() {
        pageBuffer.reservePageID(infoPageID);
        CMVBTInfoPage<K> infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, true,
            internalOwner);
        assert infoPage != null;
        readInfoPage(infoPage);
        pageBuffer.unfix(infoPage, internalOwner);

        stableVer = maxCommittedTransactionID;
        tmvbt.forwardCommittedVersion(stableVer);
    }

    protected void readInfoPage(CMVBTInfoPage<K> infoPage) {
        this.maxCommittedTransactionID = infoPage.getMaxCommittedVersion();
        this.range = infoPage.getKeyRange();
    }

    protected void writeInfoPage(CMVBTInfoPage<K> infoPage) {
        infoPage.setMaxCommittedVersion(maxCommittedTransactionID);
        infoPage.setKeyRange(range);
    }

    @Override
    protected void updateInfoPage(Owner owner) {
        CMVBTInfoPage<K> infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, false, owner);
        assert infoPage != null;
        writeInfoPage(infoPage);
        pageBuffer.unfix(infoPage, owner);
    }

    @Override
    public int getHeight(int readVersion) {
        return tmvbt.getHeight(readVersion);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Tree<K, V, MVBTPage<K, V>> getVersionTree(int version) {
        if (version == Integer.MAX_VALUE) {
            // Really nasty piece of work here: forcing the VBT into a totally
            // different tree form. Oh well.
            return (Tree<K, V, MVBTPage<K, V>>) (Object) vbt;
        } else {
            return new SingleVersionTree<K, V, MVBTPage<K, V>>(this, version);
        }
    }

    @Override
    public boolean contains(K key, Transaction<K, V> tx) {
        if (log.isDebugEnabled())
            log.debug("CMVBT action: contains " + key + " @ " + tx.getReadVersion());
        V value = operations.get(key, tx);
        return value != null;
    }

    @Override
    public boolean insert(K key, V value, PagePath<K, V, MVBTPage<K, V>> savedPath,
        Transaction<K, V> tx) {
        if (log.isDebugEnabled())
            log.debug("CMVBT action: contains " + key + " @ " + tx.getTransactionID());
        return operations.insert(key, value, tx);
    }

    @Override
    public boolean delete(K key, PagePath<K, V, MVBTPage<K, V>> savedPath, Transaction<K, V> tx) {
        if (log.isDebugEnabled())
            log.debug("CMVBT action: delete " + key + " @ " + tx.getTransactionID());
        return operations.delete(key, tx);
    }

    @Override
    public boolean getRange(KeyRange<K> range, Callback<Pair<K, V>> callback, Transaction<K, V> tx) {
        return operations.getRange(range, callback, tx);
    }

    @Override
    public V get(K key, Transaction<K, V> tx) {
        if (log.isDebugEnabled())
            log.debug("CMVBT action: get " + key + " @ " + tx.getReadVersion());
        return operations.get(key, tx);
    }

    @Override
    public MVBTPage<K, V> getRoot(Owner owner) {
        return tmvbt.getRoot(owner);
    }

    @Override
    public PageID getRootPageID() {
        return tmvbt.getRootPageID();
    }

    @Override
    public void checkConsistency(Object... params) {
        tmvbt.checkConsistency();
        vbt.checkConsistency();
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
        System.out.println("TMVBT: " + tmvbt);
        tmvbt.printDebugInfo();

        System.out.println("VBT: " + vbt);
        vbt.printDebugInfo();
        long vbtEntries = TreeShortcuts.countEntries(vbt);
        System.out.println("Entries in VBT: " + vbtEntries);

        System.out.println("CTS: " + cts);
    }

    @Override
    public void attachRoot(MVBTPage<K, V> root, Transaction<K, V> tx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearInternal(Transaction<K, V> tx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MVKeyRange<K> getKeyRange() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MVBTPage<K, V> getRoot(int version, Owner owner) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PageID getRootPageID(int version) {
        return tmvbt.getRootPageID(version);
    }

    @Override
    public void traverseMVPages(Predicate<MVKeyRange<K>> predicate,
        Callback<Page<K, V>> operation, Owner owner) {
        while (hasTransientCommits()) {
            runMaintenanceTransaction();
        }
        tmvbt.traverseMVPages(predicate, operation, owner);
    }

    @Override
    public MVTreeOperations<K, V, MVBTPage<K, V>> getOperations() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MVBTPage<K, V> getPage(K key, int version, Owner owner) {
        return tmvbt.getPage(key, version, owner);
    }

    @Override
    public MVBTPage<K, V> getPage(K key, Owner owner) {
        return tmvbt.getPage(key, owner);
    }

    public void runMaintenanceTransaction() {
        operations.runMaintenanceTransaction();
    }

    public boolean needMaintenance() {
        return hasTransientCommits();
    }

    @Override
    public void setStatisticsLogger(StatisticsLogger stats) {
        // vbt.setStatisticsLogger(NoStatistics.instance());
        vbt.setStatisticsLogger(stats);

        super.setStatisticsLogger(stats);
        tmvbt.setStatisticsLogger(stats);
    }

    @Override
    public String toString() {
        return String.format(
            "CMVB tree wrapper, stable ver: %d, max.com: %d, has transients: %s", stableVer,
            maxCommittedTransactionID, hasTransientCommits() ? "yes" : "no");
    }

    @Override
    public Pair<K, V> floorEntry(K key, Transaction<K, V> tx) {
        throw new NotImplementedException();
    }

    @Override
    public Pair<K, V> nextEntry(final K key, PagePath<K, V, MVBTPage<K, V>> savedPath,
        Transaction<K, V> tx) {
        // Simple implementation: find next entry using range query
        return TreeShortcuts.nextEntry(this, key, tx);
    }

}
