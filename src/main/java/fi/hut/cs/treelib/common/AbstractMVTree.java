package fi.hut.cs.treelib.common;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVPage;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.MVTreeOperations;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.internal.CountAliveEntriesOperation;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.util.MVKeyRangePredicate;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public abstract class AbstractMVTree<K extends Key<K>, V extends PageValue<?>, P extends AbstractMVTreePage<K, V, P> & MVPage<K, V, P>>
    extends AbstractTree<K, V, P> implements MVTree<K, V, P> {

    private static final Logger log = Logger.getLogger(AbstractMVTree.class);

    protected AbstractMVTree(String identifier, String name, PageID infoPageID,
        DatabaseConfiguration<K, V> dbConfig) {
        super(identifier, name, infoPageID, dbConfig);
    }

    /**
     * ACTION: get @ version
     */
    @Override
    public V get(K key, Transaction<K, V> tx) {
        log.debug("MV-Tree action: get " + key + "@" + tx.getReadVersion());
        PageID rootPageID = getRootPageID(tx.getReadVersion());
        if (rootPageID == null)
            return null;
        V value = getInternal(key, rootPageID, tx);
        return value;
    }

    /**
     * ACTION: contains
     */
    @Override
    public boolean contains(K key, Transaction<K, V> tx) {
        log.debug("MV-Tree action: contains " + key);
        PageID rootPageID = getRootPageID(tx.getReadVersion());
        if (rootPageID == null)
            return false;
        boolean result = getInternal(key, rootPageID, tx) != null;
        return result;
    }

    @Override
    public boolean getRange(KeyRange<K> range, Callback<Pair<K, V>> callback, Transaction<K, V> tx) {
        P root = getRoot(tx.getReadVersion(), tx);
        if (root == null)
            return true;
        boolean result = root.getRange(range, callback, tx);
        pageBuffer.unfix(root, tx);
        return result;
    }

    @SuppressWarnings("unchecked")
    private V getInternal(K key, PageID rootPageID, Transaction<K, V> tx) {
        if (rootPageID == null)
            return null;
        MVTreeOperations<K, V, P> operations = getOperations();
        // Get operation: enough to traverse using latch-coupling
        PagePath<K, V, P> path = new PagePath<K, V, P>(false);
        operations.findPathToLeafPage(rootPageID, key, path, tx.getReadVersion(), tx);
        P leaf = path.getCurrent();
        assert leaf != null;
        V value = (V) leaf.getEntry(key, tx);
        pageBuffer.unfix(path, tx);
        return value;
    }

    /**
     * Returns the leaf page whose key range contains the given key (at the
     * given version).
     * 
     * @return the leaf page, fixed to buffer; or null (if the entire tree is
     * empty). Remember to unfix the page after use!
     */
    @Override
    public P getPage(K key, int version, Owner owner) {
        PageID rootPageID = getRootPageID(version);
        if (rootPageID == null)
            return null;

        MVTreeOperations<K, V, P> operations = getOperations();
        // Latch-coupling traversal is enough
        PagePath<K, V, P> path = new PagePath<K, V, P>(false);
        operations.findPathToLeafPage(rootPageID, key, path, version, owner);
        P leaf = path.getCurrent();
        if (leaf != null) {
            // Ascend to parent page so that leaf page is not unfixed in the
            // unfixPath() call
            path.ascend();
        }
        pageBuffer.unfix(path, owner);
        return leaf;
    }

    /**
     * @return the page that contains the given key, fixed to buffer.
     */
    @Override
    public P getPage(K key, Owner owner) {
        return getPage(key, getLatestVersion(), owner);
    }

    @Override
    public int getLatestVersion() {
        return getCommittedVersion();
    }

    @Override
    public final int getFirstVersion() {
        return 0;
    }

    @Override
    public final void deleteRoot(P root, Owner owner) {
        throw new UnsupportedOperationException("Root deleting not supported");
    }

    /**
     * @param root the new root. Must have at least one fix. This method adds
     * a new fix which is reserved for releasing by this operation (when
     * replacing the root with a new one). Callers must release the fix(es)
     * they have acquired themselves.
     */
    @Override
    public abstract void attachRoot(P root, Transaction<K, V> tx);

    /**
     * @return the fixed root page. Release after use!
     */
    public P createIndexRoot(P page1, P page2, Transaction<K, V> tx) {
        // if (page1.keyRange > page2.keyRange)
        if (page1.getKeyRange().compareTo(page2.getKeyRange()) > 0) {
            return createIndexRoot(page2, page1, tx);
        }
        assert page1.getHeight() == page2.getHeight() : page1.getHeight() + ", "
            + page2.getHeight();
        int newHeight = page1.getHeight() + 1;

        // Create and attach the new root to the tree
        P newRoot = createIndexRoot(newHeight, tx);
        // The new root must have space for the two pages, so no path info is
        // needed. This is a special case that is handled in insertRouter()
        newRoot.insertRouter(page1, null, tx);
        newRoot.insertRouter(page2, null, tx);
        return newRoot;
    }

    public abstract void clearInternal(Transaction<K, V> tx);

    /**
     * Must be overridden, because now we are dealing with multiversion key
     * ranges.
     * 
     * TODO: AbstractTree also defined extendKeyRange() etc; perhaps those
     * should also be changed (change forced).
     */
    @Override
    public abstract MVKeyRange<K> getKeyRange();

    @Override
    public int countAliveEntries() {
        P root = getRoot(internalOwner);
        if (root == null)
            return 0;
        CountAliveEntriesOperation<K, V> op = new CountAliveEntriesOperation<K, V>(
            getLatestVersion());
        MVKeyRangePredicate<K> pred = new MVKeyRangePredicate<K>(keyPrototype);
        pred.setRestrictedVersionRange(KeyRangeImpl
            .getKeyRange(new IntegerKey(getLatestVersion())));
        traverseMVPages(pred, op, internalOwner);
        pageBuffer.unfix(root, internalOwner);
        return op.getEntryCount();
    }

    @Override
    public void traversePages(Predicate<KeyRange<K>> predicate, Callback<Page<K, V>> operation,
        Owner owner) {
        // Overridden method, no version range specified
        // Create a wrapper predicate that accepts all versions
        MVKeyRangePredicate<K> pred = new MVKeyRangePredicate<K>(getKeyPrototype(), predicate);
        traverseMVPages(pred, operation, owner);
    }

    public abstract void traverseMVPages(Predicate<MVKeyRange<K>> predicate,
        Callback<Page<K, V>> operation, Owner owner);

}
