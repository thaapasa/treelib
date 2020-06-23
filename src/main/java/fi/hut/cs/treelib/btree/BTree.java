package fi.hut.cs.treelib.btree;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.OrderedTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.VisualizableTree;
import fi.hut.cs.treelib.common.AbstractTree;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.TreeShortcuts;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.tuska.util.Callback;
import fi.tuska.util.Holder;
import fi.tuska.util.Pair;

public class BTree<K extends Key<K>, V extends PageValue<?>> extends
    AbstractTree<K, V, BTreePage<K, V>> implements Tree<K, V, BTreePage<K, V>>,
    OrderedTree<K, V, BTreePage<K, V>>, Component, VisualizableTree<K, V, BTreePage<K, V>> {

    private static final Logger log = Logger.getLogger(BTree.class);

    /**
     * The current root page. This page is always kept fixed to the page
     * buffer for efficiency.
     */
    private BTreePage<K, V> root;

    private final BTreeOperations<K, V> operations;

    private final PageFactory<BTreeInfoPage> infoPageFactory;

    protected boolean showLeafPageValues = false;

    private byte[] extraData;

    public BTree(String identifier, String name, PageID infoPageID,
        DatabaseConfiguration<K, V> dbConfig) {
        super(identifier, name, infoPageID, dbConfig);

        this.root = null;

        this.infoPageFactory = new BTreeInfoPage(dbConfig.getPageSize());
        PageBuffer.registerPageFactory(infoPageFactory);
        // Initializes the page factory. Must be called before the
        // operations-object is created.
        PageFactory<BTreePage<K, V>> factory = new BTreePageFactory<K, V>(this, dbConfig
            .getPageSize());
        initialize(factory);

        this.operations = new BTreeOperations<K, V>(this);

        loadTree();
    }

    public BTree(PageID infoPageID, DatabaseConfiguration<K, V> dbConfig) {
        this("btree", "B-tree", infoPageID, dbConfig);
    }

    public void setOverwriteEntries(boolean state) {
        operations.setOverwriteEntries(state);
    }

    @Override
    protected void loadTree() {
        // Check that info page is reserved
        pageBuffer.reservePageID(infoPageID);

        BTreeInfoPage infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, true,
            internalOwner);

        if (infoPage.getRootPageID().intValue() > 0) {
            // Load root page
            root = pageBuffer
                .fixPage(infoPage.getRootPageID(), pageFactory, false, internalOwner);
            assert root != null;
            assert root.isRoot() : root;
        }
        extraData = infoPage.getExtraData();

        pageBuffer.unfix(infoPage, internalOwner);
    }

    @Override
    public BTreeOperations<K, V> getOperations() {
        return operations;
    }

    @Override
    public int getHeight() {
        return root != null ? root.getHeight() : 0;
    }

    /**
     * For modifications of the B-tree: sets an extra data array that is
     * stored in the B-tree information page.
     */
    protected void setExtraData(byte[] extraData, Owner owner) {
        this.extraData = extraData;
        updateInfoPage(owner);
    }

    protected byte[] getExtraData() {
        return extraData;
    }

    /**
     * Returns the current root (fixed). Release after use!
     */
    @Override
    public BTreePage<K, V> getRoot(Owner owner) {
        if (root == null)
            return null;
        return pageBuffer.fixPage(root.getPageID(), pageFactory, false, owner);
    }

    @Override
    public PageID getRootPageID() {
        return (root != null) ? root.getPageID() : null;
    }

    @Override
    public boolean isEmpty(Transaction<K, V> tx) {
        return root == null;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public void deleteRoot(BTreePage<K, V> page, Owner owner) {
        if (root != page)
            throw new IllegalArgumentException("Trying to delete incorrect root page!");
        if (root != null) {
            pageBuffer.delete(root.getPageID(), owner);
            root = null;
            updateInfoPage(owner);
        }
    }

    /**
     * ACTION: Deletes a value from the B-tree.
     */
    @Override
    public boolean delete(K key, PagePath<K, V, BTreePage<K, V>> savedPath, Transaction<K, V> tx) {
        if (root == null) {
            log.debug("Trying to delete, root is null");
            return false;
        }
        assert savedPath != null;
        // Entire path is needed now because we may have to do SMOs
        savedPath = operations.validatePathToLeafPage(root.getPageID(), key, savedPath, tx);
        V result = operations.delete(savedPath, key, tx);
        return result != null;
    }

    /**
     * ACTION: Gets a value from the B-tree.
     */
    @Override
    @SuppressWarnings("unchecked")
    public V get(K key, Transaction<K, V> tx) {
        log.debug("B-Tree action: get " + key);
        if (root == null) {
            return null;
        }
        PagePath<K, V, BTreePage<K, V>> path = new PagePath<K, V, BTreePage<K, V>>(false);
        // Traversal is enough, no need to store entire path
        operations.findPathToLeafPage(root.getPageID(), key, path, DEFAULT_TRANSACTION_ID, tx);
        BTreePage<K, V> leaf = path.getCurrent();
        assert leaf != null : "Could not find a leaf node for key " + key;
        assert leaf.isLeafPage();
        V result = (V) leaf.getEntry(key);
        pageBuffer.unfix(path, tx);
        return result;
    }

    @Override
    public Pair<K, V> nextEntry(final K key, PagePath<K, V, BTreePage<K, V>> savedPath,
        Transaction<K, V> tx) {
        if (log.isDebugEnabled())
            log.debug("B-Tree action: next entry from " + key + ", saved path " + savedPath);
        if (root == null)
            return null;
        assert savedPath != null;
        savedPath = operations.validatePathToLeafPage(root.getPageID(), key, savedPath, tx);
        assert savedPath != null;

        BTreePage<K, V> leaf = savedPath.getCurrent();
        assert leaf != null : "Could not find a leaf node for key " + key;
        assert leaf.isLeafPage();
        if (leaf.getKeyRange().getMax().compareTo(key) < 0)
            throw new IllegalArgumentException("Saved path is not correct for the given key; "
                + leaf + " is before " + key);

        final Holder<Pair<K, V>> found = new Holder<Pair<K, V>>();
        operations.traverseLeafEntries(savedPath, key, new Callback<Pair<K, V>>() {
            @Override
            public boolean callback(Pair<K, V> entry) {
                K cur = entry.getFirst();
                // Skip to next key
                if (cur.equals(key))
                    return true;

                found.setValue(entry);
                // False to stop search (next value found)
                return false;
            }
        }, tx);

        return found.getValue();
    }

    @Override
    public Pair<K, V> floorEntry(K key, Transaction<K, V> tx) {
        log.debug("B-Tree action: floor entry of " + key);
        if (root == null) {
            return null;
        }
        K findKey = key;
        // TODO: This might be enhanced by not restarting the entire query,
        // but only backing up a bit
        while (true) {
            // Traversal is enough, no need to store entire path
            PagePath<K, V, BTreePage<K, V>> path = new PagePath<K, V, BTreePage<K, V>>(false);
            operations.findPathToLeafPage(root.getPageID(), findKey, path,
                DEFAULT_TRANSACTION_ID, tx);
            BTreePage<K, V> leaf = path.getCurrent();
            assert leaf != null : "Could not find a leaf node for key " + findKey;
            assert leaf.isLeafPage();
            // Get result and leaf page minimum key
            Pair<K, V> result = leaf.floorEntry(findKey);
            findKey = leaf.getKeyRange().getMin();
            // Unfix fixed pages
            pageBuffer.unfix(path, tx);

            // Return result if we found it already
            if (result != null) {
                return result;
            }
            // If we are at minimum page, there is no result
            if (findKey.equals(keyPrototype.getMinKey())) {
                return null;
            }
            // Not in this node, but in previous.
            // Restart search using key = leaf node min - 1
            findKey = findKey.previousKey();
        }
    }

    @Override
    public boolean getRange(KeyRange<K> range, Callback<Pair<K, V>> callback, Transaction<K, V> tx) {
        if (root == null) {
            return true;
        }
        PagePath<K, V, BTreePage<K, V>> path = new PagePath<K, V, BTreePage<K, V>>(true);
        operations.findPathToLeafPage(root.getPageID(), range.getMin(), path,
            DEFAULT_TRANSACTION_ID, tx);
        BTreePage<K, V> leaf = path.getCurrent();
        boolean result = leaf.getRange(range, callback, tx);
        pageBuffer.unfix(path, tx);
        return result;
    }

    /**
     * ACTION: Checks if the B-tree contains a key.
     */
    @Override
    public boolean contains(K key, Transaction<K, V> tx) {
        log.debug("B-Tree action: contains " + key);
        if (root == null) {
            return false;
        }
        PagePath<K, V, BTreePage<K, V>> path = new PagePath<K, V, BTreePage<K, V>>(false);
        // Traversal is enough, no need to hold entire path
        operations.findPathToLeafPage(root.getPageID(), key, path, DEFAULT_TRANSACTION_ID, tx);
        BTreePage<K, V> leaf = path.getCurrent();
        boolean result = leaf.contains(key);
        pageBuffer.unfix(path, tx);
        return result;
    }

    /**
     * Requires the old root to be fixed (as it is always), and also the new
     * node. Will unfix the old root node and leave the new root fixed to page
     * buffer. Will also fix the root again (so that this method can unfix
     * it). Therefore, proper usage is: 1. fix/create new root page r, 2.
     * attachRoot(r), 3. release root page r.
     * 
     * @param node the new root. Must be fixed to buffer.
     */
    @Override
    protected void attachRoot(BTreePage<K, V> node, Transaction<K, V> tx) {
        assert !node.isRoot() : String.format(
            "Trying to attach node %s which has already been attached", node.getName());

        if (this.root != null) {
            // Unfix old root from the buffer
            pageBuffer.unfix(root, internalOwner);
        }
        this.root = node;
        pageBuffer.fixPage(node.getPageID(), pageFactory, false, internalOwner);
        node.setRoot(true);
        // Update info page
        updateInfoPage(tx);
    }

    @Override
    protected void updateInfoPage(Owner owner) {
        BTreeInfoPage infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, false, owner);
        assert infoPage != null;
        // setRootPageID() sets the page dirty
        infoPage.setRootPageID(root != null ? root.getPageID() : PageID.INVALID_PAGE_ID);
        infoPage.setExtraData(extraData);
        pageBuffer.unfix(infoPage, owner);
    }

    @Override
    public String toString() {
        return String.format("B-Tree (%d%%, %d B, h: %d), root: %s", (int) (dbConfig
            .getSMOPolicy().getMinFillRate() * 100), dbConfig.getPageSize(), getHeight(),
            (root != null ? root.getName() : "none"));
    }

    @Override
    public int getMaxHeight() {
        return root.getHeight();
    }

    /**
     * Only used for visualization. Does not follow proper page fixing
     * policies.
     */
    @Override
    public Collection<VisualizablePage<K, V>> getPagesAtHeight(int height) {
        SortedSet<VisualizablePage<K, V>> nodes = new TreeSet<VisualizablePage<K, V>>();
        if (root != null) {
            root.collectPagesAtHeight(height, nodes);
        }
        return nodes;
    }

    /**
     * @return the fixed leaf page whose key range contains the given key
     */
    @Override
    public BTreePage<K, V> getPage(K key, Owner owner) {
        if (root == null) {
            return null;
        }
        // Traversal is enough, no need to hold entire path
        PagePath<K, V, BTreePage<K, V>> path = new PagePath<K, V, BTreePage<K, V>>(false);
        operations.findPathToLeafPage(root.getPageID(), key, path, DEFAULT_TRANSACTION_ID, owner);
        BTreePage<K, V> leaf = path.getCurrent();
        if (leaf != null) {
            // Ascend so that leaf page is not unfixed below
            path.ascend();
        }
        assert path.isEmpty();
        return leaf;
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);

        long pages = TreeShortcuts.countPages(this, false);
        System.out.println(getName() + " size (pages): " + pages);
    }

    @Override
    public boolean isMultiVersion() {
        return false;
    }

}
