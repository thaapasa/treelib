package fi.hut.cs.treelib.btree;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.common.AbstractTree;
import fi.hut.cs.treelib.common.CounterCallback;
import fi.hut.cs.treelib.common.DatabaseConfigurationImpl;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.hut.cs.treelib.common.VersionedKey;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.tuska.util.Callback;
import fi.tuska.util.Holder;
import fi.tuska.util.NotImplementedException;
import fi.tuska.util.Pair;

/**
 * Models a versioned B-tree that is used as a multiversion storage.
 * 
 * @author thaapasa
 */
public class MVVBTree<K extends Key<K>, V extends PageValue<?>> extends
    AbstractTree<K, V, BTreePage<K, V>> implements MVTree<K, V, BTreePage<K, V>> {

    private static final Logger log = Logger.getLogger(MVVBTree.class);

    private final BTree<VersionedKey<K>, UpdateMarker<V>> tree;

    private int committedVersion;
    private int activeVersion;

    public MVVBTree(PageID infoPageID, DatabaseConfiguration<K, V> dbConfig) {
        super("mvvbt", "MV VBT", infoPageID, dbConfig);

        DatabaseConfiguration<VersionedKey<K>, UpdateMarker<V>> btConfig = new DatabaseConfigurationImpl<VersionedKey<K>, UpdateMarker<V>>(
            dbConfig, new VersionedKey<K>(dbConfig.getKeyPrototype(), 0), UpdateMarker
                .createInsert(dbConfig.getValuePrototype()), dbConfig.getSMOPolicy());

        this.tree = new BTree<VersionedKey<K>, UpdateMarker<V>>(infoPageID, btConfig);
        tree.setOverwriteEntries(true);

        loadTree();
    }

    @Override
    public boolean contains(K key, Transaction<K, V> tx) {
        V value = get(key, tx);
        return value != null;
    }

    @Override
    public V get(K key, Transaction<K, V> tx) {
        int version = tx.getReadVersion();
        Pair<VersionedKey<K>, UpdateMarker<V>> entry = tree.floorEntry(new VersionedKey<K>(key,
            version), getTransaction(tx));
        if (entry == null)
            return null;
        VersionedKey<K> fkey = entry.getFirst();
        if (!fkey.getKey().equals(key)) {
            // Previous (k,v) pair in the tree has a different key
            return null;
        }

        assert (fkey.getVersion() <= version);
        UpdateMarker<V> val = entry.getSecond();

        if (val.isDelete())
            return null;
        return val.getValue();
    }

    @Override
    public boolean getRange(final KeyRange<K> range, final Callback<Pair<K, V>> callback,
        Transaction<K, V> tx) {
        final Holder<Pair<VersionedKey<K>, UpdateMarker<V>>> lastEntry = new Holder<Pair<VersionedKey<K>, UpdateMarker<V>>>();
        final int version = tx.getReadVersion();

        VersionedKey<K> minKey = new VersionedKey<K>(range.getMin(), 0);
        VersionedKey<K> maxKey = new VersionedKey<K>(range.getMax(), 0);

        // Find all the possible updates in the given key range
        boolean result = tree.getRange(new KeyRangeImpl<VersionedKey<K>>(minKey, maxKey),
            new Callback<Pair<VersionedKey<K>, UpdateMarker<V>>>() {
                /**
                 * Called for each versioned update marker entry in the
                 * backing VBT
                 */
                @Override
                public boolean callback(Pair<VersionedKey<K>, UpdateMarker<V>> entry) {
                    if (log.isDebugEnabled())
                        log.debug("Processing object " + entry);
                    if (lastEntry.getValue() != null) {
                        // Check if we have passed an entry border
                        K curKey = entry.getFirst().getKey();
                        K lastKey = lastEntry.getValue().getFirst().getKey();

                        if (!curKey.equals(lastKey)) {
                            // Passed on to next key, callback the last entry
                            if (!callbackIfMatch(lastEntry.getValue(), version, range, callback))
                                return false;
                        } else {
                            int lastVer = lastEntry.getValue().getFirst().getVersion();
                            int curVer = entry.getFirst().getVersion();
                            assert curVer > lastVer;
                            if (curVer > version) {
                                // Passed on to versions beyond the search,
                                // callback the last entry
                                if (!callbackIfMatch(lastEntry.getValue(), version, range,
                                    callback))
                                    return false;
                            }
                        }
                    }

                    lastEntry.setValue(entry);
                    return true;
                }
            }, getTransaction(tx));
        if (!result)
            return false;

        // Check if the lastEntry should be callback'd
        return callbackIfMatch(lastEntry.getValue(), version, range, callback);
    }

    private boolean callbackIfMatch(Pair<VersionedKey<K>, UpdateMarker<V>> entry, int version,
        KeyRange<K> originalRange, Callback<Pair<K, V>> callback) {
        if (log.isDebugEnabled())
            log.debug("Checking if should callback " + entry);
        if (entry == null)
            // True to continue
            return true;
        if (entry.getFirst().getVersion() <= version) {
            // Version is a match
            K key = entry.getFirst().getKey();
            assert originalRange.contains(key) : key + " not in " + originalRange;
            if (entry.getSecond().isInsert()) {
                if (log.isDebugEnabled())
                    log.debug(String.format("Callback for %s, %s", entry.getFirst().getKey(),
                        entry.getSecond().getValue()));
                // Last entry was an insertion, so do a callback and return
                // the value given by the callback
                return callback.callback(new Pair<K, V>(entry.getFirst().getKey(), entry
                    .getSecond().getValue()));
            }
        }
        // Entry was skipped without processing the callback, return true to
        // continue
        return true;
    }

    @Override
    public boolean delete(K key, PagePath<K, V, BTreePage<K, V>> savedPath, Transaction<K, V> tx) {
        return tree.insert(new VersionedKey<K>(key, tx.getReadVersion()), UpdateMarker
            .createDelete(dbConfig.getValuePrototype()), getPath(savedPath), getTransaction(tx));
    }

    @Override
    public boolean insert(K key, V value, PagePath<K, V, BTreePage<K, V>> savedPath,
        Transaction<K, V> tx) {
        return tree.insert(new VersionedKey<K>(key, tx.getReadVersion()), UpdateMarker
            .createInsert(value), getPath(savedPath), getTransaction(tx));
    }

    @Override
    public MVKeyRange<K> getKeyRange() {
        KeyRange<VersionedKey<K>> range = tree.getKeyRange();
        K minKey = range.getMin().getKey();
        K maxKey = range.getMax().getKey();
        return new MVKeyRange<K>(minKey, maxKey.nextKey(), 0, activeVersion + 1);
    }

    @SuppressWarnings("unchecked")
    private PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>> getPath(
        PagePath<K, V, BTreePage<K, V>> savedPath) {
        Object o = savedPath;
        return (PagePath<VersionedKey<K>, UpdateMarker<V>, BTreePage<VersionedKey<K>, UpdateMarker<V>>>) o;
    }

    private Transaction<VersionedKey<K>, UpdateMarker<V>> getTransaction(Transaction<K, V> tx) {
        return new DummyTransaction<VersionedKey<K>, UpdateMarker<V>>(tx.getReadVersion(), tx
            .getTransactionID(), tx);
    }

    @Override
    public MVVBTOperations<K, V> getOperations() {
        return null;
    }

    @Override
    public int countAliveEntries() {
        Transaction<K, V> tx = new DummyTransaction<K, V>(activeVersion, 0, internalOwner);
        CounterCallback<Pair<K, V>> cb = new CounterCallback<Pair<K, V>>();
        getRange(dbConfig.getKeyPrototype().getEntireRange(), cb, tx);
        return (int) cb.getCount();
    }

    @Override
    public int getCommittedVersion() {
        return committedVersion;
    }

    @Override
    public int getFirstVersion() {
        return 0;
    }

    @Override
    public int getHeight(int version) {
        return tree.getHeight();
    }

    @Override
    public int getLatestVersion() {
        return getCommittedVersion();
    }

    @Override
    @SuppressWarnings("unchecked")
    public BTreePage<K, V> getPage(K key, int version, Owner owner) {
        // Real nasty conversion here
        return (BTreePage<K, V>) tree.getPage(new VersionedKey<K>(key, version), owner);
    }

    @Override
    @SuppressWarnings("unchecked")
    public BTreePage<K, V> getRoot(int version, Owner owner) {
        // Real nasty conversion here
        return (BTreePage<K, V>) tree.getRoot(owner);
    }

    @Override
    public PageID getRootPageID(int version) {
        return tree.getRootPageID();
    }

    @Override
    public Tree<K, V, BTreePage<K, V>> getVersionTree(int readVersion) {
        return this;
    }

    @Override
    public void close() {
        super.close();
        tree.close();
    }

    @Override
    public int countAllEntries() {
        return tree.countAllEntries();
    }

    @Override
    public void flush() {
        super.flush();
        tree.flush();
    }

    @Override
    public int getHeight() {
        return tree.getHeight();
    }

    @Override
    public int getMaxHeight() {
        return tree.getMaxHeight();
    }

    @Override
    public BTreePage<K, V> getPage(K key, Owner owner) {
        return getPage(key, 0, owner);
    }

    @Override
    public PageFactory<BTreePage<K, V>> getPageFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BTreePage<K, V> getRoot(Owner owner) {
        return getRoot(0, owner);
    }

    @Override
    public PageID getRootPageID() {
        return tree.getRootPageID();
    }

    @Override
    public StatisticsLogger getStatisticsLogger() {
        return tree.getStatisticsLogger();
    }

    @Override
    public boolean isEmpty(Transaction<K, V> tx) {
        CounterCallback<Pair<K, V>> cb = new CounterCallback<Pair<K, V>>();
        getRange(dbConfig.getKeyPrototype().getEntireRange(), cb, tx);
        return cb.getCount() == 0;
    }

    @Override
    public boolean isMultiVersion() {
        return true;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public void setStatisticsLogger(StatisticsLogger stats) {
        tree.setStatisticsLogger(stats);
        super.setStatisticsLogger(stats);
    }

    @Override
    public String toString() {
        return String.format("MV-VBT of commit-version %d; backend: %s", committedVersion, tree
            .toString());
    }

    @Override
    protected void attachRoot(BTreePage<K, V> rootPage, Transaction<K, V> tx) {
        // This should not be called
        throw new NotImplementedException();
    }

    @Override
    public void deleteRoot(BTreePage<K, V> root, Owner owner) {
        // This should not be called
        throw new NotImplementedException();
    }

    @Override
    protected void loadTree() {
        // backing tree is already loaded
        byte[] data = tree.getExtraData();
        committedVersion = data.length >= 4 ? ByteBuffer.wrap(data).getInt() : 0;
        activeVersion = committedVersion;
        log.info("Loaded MV-VBT with committed version " + committedVersion);
    }

    @Override
    protected void updateInfoPage(Owner owner) {
        log.debug("Updating the info page of backing B-tree with committed version "
            + committedVersion);
        byte[] data = new byte[4];
        ByteBuffer.wrap(data).putInt(committedVersion);
        tree.setExtraData(data, owner);
    }

    public int beginNewTransaction() {
        assert activeVersion >= committedVersion;
        if (activeVersion > committedVersion)
            throw new IllegalStateException(
                "Cannot have more than one active updating transaction on MV-VBT");
        return ++activeVersion;
    }

    public int commitActiveTransaction(Owner owner) {
        assert activeVersion >= committedVersion;
        if (activeVersion <= committedVersion)
            throw new IllegalStateException("No active transaction to commit");
        ++committedVersion;
        assert committedVersion == activeVersion;
        updateInfoPage(owner);
        log.debug("Committed version " + committedVersion);
        return committedVersion;
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
    }

    @Override
    public void checkConsistency(Object... params) {
        tree.checkConsistency(params);
    }

}
