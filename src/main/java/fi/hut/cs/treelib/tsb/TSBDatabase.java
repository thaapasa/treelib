package fi.hut.cs.treelib.tsb;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVDatabase;
import fi.hut.cs.treelib.OrderedTransaction;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.common.AbstractDatabase;
import fi.hut.cs.treelib.common.DatabaseConfigurationImpl;
import fi.hut.cs.treelib.common.DummySMOPolicy;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.NonThrashingSMOPolicy;
import fi.hut.cs.treelib.common.OrderedTransactionImpl;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.concurrency.DefaultLatchManager;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.tsb.TSBOperations.SplitPolicy;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;

public class TSBDatabase<K extends Key<K>, V extends PageValue<?>> extends
    AbstractDatabase<K, V, TSBPage<K, V>> implements MVDatabase<K, V, TSBPage<K, V>> {

    private static final Logger log = Logger.getLogger(TSBDatabase.class);

    /** Info page has PageID 1. */
    private static final PageID INFO_PAGE_ID = new PageID(1);
    /** PTT info page has PageID 2. */
    private static final PageID PTT_INFO_PAGE_ID = new PageID(2);

    private final SplitPolicy splitPolicy;
    private final boolean batchPTTUpdate;

    private static final SMOPolicy PTT_SMO_POLICY = new NonThrashingSMOPolicy(0.2, 0.2);

    public TSBDatabase(int bufferSize, K keyPrototype, V valuePrototype, SplitPolicy splitPolicy,
        boolean batchPTTUpdate, PageStorage pageStorage) {
        super(bufferSize, pageStorage, new DummySMOPolicy(), new DefaultLatchManager(),
            keyPrototype, valuePrototype);

        this.splitPolicy = splitPolicy;
        this.batchPTTUpdate = batchPTTUpdate;
        initStructures();
    }

    @Override
    protected void initStructures() {
        PageBuffer pageBuffer = getPageBuffer();
        pageBuffer.reservePageID(INFO_PAGE_ID);
        pageBuffer.reservePageID(PTT_INFO_PAGE_ID);

        BTree<IntegerKey, IntegerValue> ptt = new BTree<IntegerKey, IntegerValue>(
            PTT_INFO_PAGE_ID, getPTTConfig());
        TSBTree<K, V> tree = new TSBTree<K, V>(INFO_PAGE_ID, splitPolicy, batchPTTUpdate, ptt,
            this);
        initialize(tree);

        pageBuffer.registerPageFlushListener(tree);
    }

    @Override
    protected void clearStructures() {
        // Tree cleared in AbstractDatabase.close()
    }

    private DatabaseConfiguration<IntegerKey, IntegerValue> getPTTConfig() {
        return new DatabaseConfigurationImpl<IntegerKey, IntegerValue>(this,
            IntegerKey.PROTOTYPE, IntegerValue.PROTOTYPE, PTT_SMO_POLICY);
    }

    @Override
    public OrderedTransaction<K, V> beginTransaction() {
        getStatisticsLogger().log(GlobalOperation.GO_NEW_TRANSACTION);
        OrderedTransaction<K, V> tx = new OrderedTransactionImpl<K, V, TSBPage<K, V>>(this,
            getDatabaseTree().getCommittedVersion(), false);
        getDatabaseTree().beginTransaction(tx);
        return tx;
    }

    @Override
    public OrderedTransaction<K, V> beginReadTransaction(int version) {
        getStatisticsLogger().log(GlobalOperation.GO_NEW_TRANSACTION);
        OrderedTransaction<K, V> tx = new OrderedTransactionImpl<K, V, TSBPage<K, V>>(this,
            version, true);
        getDatabaseTree().beginTransaction(tx);
        return tx;
    }

    @Override
    public int commit(Transaction<K, V> tx) {
        if (tx.isReadOnly()) {
            // Read-only transactions require no special actions
            return tx.getReadVersion();
        }
        int commitVer = getDatabaseTree().commitTransaction(tx);
        log.debug(String.format("Committed transaction %d to final commit-version %d", tx
            .getTransactionID(), commitVer));

        return commitVer;
    }

    @Override
    public TSBTree<K, V> getDatabaseTree() {
        return (TSBTree<K, V>) super.getDatabaseTree();
    }

    @Override
    public TSBDatabase<K, V> getMVDatabase() {
        return this;
    }

    @Override
    public int getCommittedVersion() {
        return getDatabaseTree().getCommittedVersion();
    }

    @Override
    public Set<Integer> getSeparateRootedVersions() {
        Set<Integer> set = new HashSet<Integer>();
        set.add(1);
        return set;
    }

    @Override
    public void traversePages(Predicate<KeyRange<K>> predicate, Callback<Page<K, V>> operation,
        Owner owner) {
        tree.traversePages(predicate, operation, owner);
    }

    @Override
    public boolean isMultiVersion() {
        return true;
    }

    @Override
    public boolean isMultiDimension() {
        return false;
    }

    @Override
    public String toString() {
        return "TSB database";
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
        System.out.println("Backing tree:");
        getDatabaseTree().printDebugInfo();
    }

    @Override
    public void requestMaintenance() {
        getDatabaseTree().flushVTTtoPTT();
        super.requestMaintenance();
    }

}
