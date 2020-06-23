package fi.hut.cs.treelib.common;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.MVTreeOperations;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.VisualizableTree;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class SingleVersionTree<K extends Key<K>, V extends PageValue<?>, P extends AbstractMVTreePage<K, V, P>>
    implements MVTree<K, V, P>, VisualizableTree<K, V, P> {

    private static final Logger log = Logger.getLogger(SingleVersionTree.class);

    private final AbstractMVTree<K, V, P> tree;
    private final int version;
    private final PageBuffer buffer;

    /** ID of the root page of this version; can be null. */
    // private final PageID rootPageID;
    public SingleVersionTree(AbstractMVTree<K, V, P> tree, int version) {
        this.tree = tree;
        this.version = version;
        // this.rootPageID = tree.getRootPageID(version);
        this.buffer = tree.getPageBuffer();
    }

    @Override
    public int getHeight() {
        P root = getRoot(tree.internalOwner);
        if (root == null)
            return 0;
        int height = root.getHeight();
        buffer.unfix(root, tree.internalOwner);
        return height;
    }

    /**
     * Returns the fixed root page. Caller must release the page!
     */
    @Override
    public P getRoot(Owner owner) {
        PageID rootID = getRootPageID();
        if (rootID == null)
            return null;

        P root = buffer.fixPage(rootID, tree.getPageFactory(), false, owner);
        assert root != null;
        return root;
    }

    @Override
    public PageID getRootPageID() {
        return tree.getRootPageID(version);
    }

    @Override
    public boolean isEmpty(Transaction<K, V> tx) {
        if (tx.getReadVersion() != this.version)
            throw new UnsupportedOperationException("Cannot range query to another version!");
        return tree.isEmpty(tx);
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public String toString() {
        int treeVersion = tree.getLatestVersion();
        PageID rootID = getRootPageID();

        return String.format("Single %s, version %d/%d, root: %s", tree.getName(), version,
            treeVersion, rootID != null ? rootID : "null");
    }

    @Override
    public int getMaxHeight() {
        P root = getRoot(tree.internalOwner);
        if (root == null)
            return 0;

        int result = root.getHeight();
        buffer.unfix(root, tree.internalOwner);
        return result;
    }

    @Override
    public Collection<VisualizablePage<K, V>> getPagesAtHeight(int height) {
        P root = getRoot(tree.internalOwner);
        if (root == null)
            return null;
        SortedSet<VisualizablePage<K, V>> nodes = new TreeSet<VisualizablePage<K, V>>();
        root.collectPagesAtHeight(height, version, nodes, tree.internalOwner);
        buffer.unfix(root, tree.internalOwner);
        return nodes;
    }

    /**
     * Forces the tree contents into the underlying storage.
     */
    @Override
    public void flush() {
        tree.flush();
    }

    @Override
    public boolean contains(K key, Transaction<K, V> tx) {
        if (tx.getReadVersion() != this.version)
            throw new UnsupportedOperationException("Cannot range query to another version!");
        return tree.contains(key, tx);
    }

    @Override
    public V get(K key, Transaction<K, V> tx) {
        if (tx.getReadVersion() != this.version)
            throw new UnsupportedOperationException("Cannot range query to another version!");
        return tree.get(key, tx);
    }

    @Override
    public boolean getRange(KeyRange<K> range, Callback<Pair<K, V>> callback, Transaction<K, V> tx) {
        if (tx.getReadVersion() != this.version)
            throw new UnsupportedOperationException("Cannot range query to another version!");
        return tree.getRange(range, callback, tx);
    }

    @Override
    public boolean insert(K key, V value, PagePath<K, V, P> savedPath, Transaction<K, V> tx) {
        return tree.insert(key, value, savedPath, tx);
    }

    @Override
    public boolean delete(K key, PagePath<K, V, P> savedPath, Transaction<K, V> tx) {
        return tree.delete(key, savedPath, tx);
    }

    @Override
    public int getCommittedVersion() {
        return tree.getCommittedVersion();
    }

    @Override
    public int getFirstVersion() {
        return tree.getFirstVersion();
    }

    @Override
    public MVKeyRange<K> getKeyRange() {
        return tree.getKeyRange();
    }

    @Override
    public P getRoot(int showVersion, Owner owner) {
        return getRoot(owner);
    }

    @Override
    public Tree<K, V, P> getVersionTree(int showVersion) {
        return this;
    }

    /**
     * @return the node that contains the given key, fixed to buffer.
     */
    @Override
    public P getPage(K key, Owner owner) {
        return tree.getPage(key, version, owner);
    }

    /**
     * @return the node that contains the given key, fixed to buffer.
     */
    @Override
    public P getPage(K key, int version, Owner owner) {
        return tree.getPage(key, this.version, owner);
    }

    @Override
    public int getHeight(int version) {
        return getHeight();
    }

    @Override
    public PageBuffer getPageBuffer() {
        return buffer;
    }

    @Override
    public PageFactory<P> getPageFactory() {
        return tree.getPageFactory();
    }

    @Override
    public MVTreeOperations<K, V, P> getOperations() {
        return tree.getOperations();
    }

    @Override
    public K getKeyPrototype() {
        return tree.getKeyPrototype();
    }

    @Override
    public V getValuePrototype() {
        return tree.getValuePrototype();
    }

    @Override
    public void setStatisticsLogger(StatisticsLogger stats) {
        log.error("SingleVersionMVBTRee.setStatisticsLogger is not supposed to be called");
    }

    @Override
    public int countAllEntries() {
        return tree.countAllEntries();
    }

    @Override
    public int countAliveEntries() {
        return tree.countAliveEntries();
    }

    @Override
    public StatisticsLogger getStatisticsLogger() {
        return tree.getStatisticsLogger();
    }

    @Override
    public String getIdentifier() {
        return "single-" + tree.getIdentifier() + "-" + version;
    }

    @Override
    public String getName() {
        return "Single " + tree.getName() + "@" + version;
    }

    @Override
    public boolean isMultiVersion() {
        return true;
    }

    @Override
    public PageID getRootPageID(int version) {
        if (version != this.version)
            throw new UnsupportedOperationException("Cannot get root page ID of another version!");
        return getRootPageID();
    }

    @Override
    public int getLatestVersion() {
        return tree.getLatestVersion();
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

}
