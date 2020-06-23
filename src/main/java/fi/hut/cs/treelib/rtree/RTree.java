package fi.hut.cs.treelib.rtree;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.VisualizableTree;
import fi.hut.cs.treelib.common.AbstractMDTree;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.internal.TreeOperations;
import fi.hut.cs.treelib.rtree.RTreeOperations.SplitType;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.tuska.util.Callback;
import fi.tuska.util.Counter;
import fi.tuska.util.Holder;
import fi.tuska.util.Pair;

public class RTree<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMDTree<K, V, MBR<K>, RTreePage<K, V>> implements MDTree<K, V, RTreePage<K, V>>,
    VisualizableTree<MBR<K>, V, RTreePage<K, V>>, Component {

    private static final Logger log = Logger.getLogger(RTree.class);

    private RTreePage<K, V> root;
    private RTreeOperations<K, V> operations;
    private final RTreePage<K, V> leafProto;

    private final PageFactory<RTreeInfoPage<K>> infoPageFactory;

    public RTree(SplitType splitType, PageID infoPageID, DatabaseConfiguration<MBR<K>, V> dbConfig) {
        super("rtree-" + splitType.toString().toLowerCase(), "R-tree", infoPageID, dbConfig);

        this.infoPageFactory = new RTreeInfoPage<K>(dbConfig.getPageSize(), keyPrototype);
        PageBuffer.registerPageFactory(infoPageFactory);

        PageFactory<RTreePage<K, V>> factory = new RTreePageFactory<K, V>(this);
        initialize(factory);

        this.operations = new RTreeOperations<K, V>(this);
        this.operations.setSplitType(splitType);

        loadTree();

        leafProto = new RTreePage<K, V>(this, PageID.INVALID_PAGE_ID);
        leafProto.format(1);
    }

    public int getLeafPageCapacity() {
        int capacity = leafProto.getPageEntryCapacity();
        return capacity;
    }

    @Override
    protected void loadTree() {
        if (pageBuffer.reservePageID(infoPageID)) {
            // The info page was not reserved, so this is a new DB
            log.info("Initializing new R-tree DB information page");
        } else {
            log.info("Loading an existing R-tree DB information page");
        }
        RTreeInfoPage<K> infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, true,
            internalOwner);
        setExtents(null);

        if (infoPage.getRootPageID().intValue() > 0) {
            // Load root page
            root = pageBuffer
                .fixPage(infoPage.getRootPageID(), pageFactory, false, internalOwner);
            assert root != null;
            assert root.isRoot();
            setExtents(infoPage.getExtents());
        }

        pageBuffer.unfix(infoPage, internalOwner);
    }

    @Override
    public V get(final MBR<K> key, Transaction<MBR<K>, V> tx) {
        final Holder<V> holder = new Holder<V>(null);
        operations.findExact(key, new Callback<Pair<MBR<K>, V>>() {
            @Override
            public boolean callback(Pair<MBR<K>, V> entry) {
                holder.setValue(entry.getSecond());
                // False to stop search
                return false;
            }
        }, tx);
        return holder.getValue();
    }

    @Override
    public boolean contains(MBR<K> key, Transaction<MBR<K>, V> tx) {
        final Counter c = new Counter();
        getExact(key, new Callback<Pair<MBR<K>, V>>() {
            @Override
            public boolean callback(Pair<MBR<K>, V> object) {
                c.advance();
                // No need to continue
                return false;
            }
        }, tx);
        return c.getCount() > 0;
    }

    @Override
    public boolean delete(MBR<K> mbr, PagePath<MBR<K>, V, RTreePage<K, V>> savedPath,
        Transaction<MBR<K>, V> tx) {
        log.debug("R-Tree action: delete " + mbr);
        if (root == null)
            return false;

        // TODO: Add saved path enhancement to R-tree deletion
        V value = operations.delete(mbr, root.getPageID(), tx);
        return value != null;
    }

    @Override
    public void deleteRoot(RTreePage<K, V> delRoot, Owner owner) {
        if (delRoot != root)
            throw new IllegalArgumentException("Trying to delete incorrect root page");
        if (root != null) {
            pageBuffer.delete(root.getPageID(), owner);
            root = null;
            updateInfoPage(owner);
        }
    }

    @Override
    public int getHeight() {
        if (root == null)
            return 0;
        return root.getHeight();

    }

    @Override
    public int getMaxHeight() {
        return getHeight();
    }

    /**
     * @return the fixed leaf page whose key range contains the given key
     */
    @Override
    public RTreePage<K, V> getPage(MBR<K> key, Owner owner) {
        if (root == null)
            return null;
        PagePath<MBR<K>, V, RTreePage<K, V>> path = new PagePath<MBR<K>, V, RTreePage<K, V>>(
            false);
        operations.findPathToLeafPage(root.getPageID(), key, path, DEFAULT_TRANSACTION_ID, owner);

        RTreePage<K, V> leaf = path.getCurrent();
        if (leaf != null) {
            // Ascend so that leaf page is not unfixed below
            path.ascend();
        }
        pageBuffer.unfix(path, owner);
        return leaf;
    }

    /**
     * Only used for visualization. Does not follow proper page fixing
     * policies.
     */
    @Override
    public Collection<VisualizablePage<MBR<K>, V>> getPagesAtHeight(int height) {
        SortedSet<VisualizablePage<MBR<K>, V>> pages = new TreeSet<VisualizablePage<MBR<K>, V>>();
        if (root != null) {
            root.collectPagesAtHeight(height, pages);
        }
        return pages;
    }

    /**
     * Returns the current root (fixed). Release after use!
     */
    @Override
    public RTreePage<K, V> getRoot(Owner owner) {
        if (root == null)
            return null;
        return pageBuffer.fixPage(root.getPageID(), pageFactory, false, owner);
    }

    @Override
    public PageID getRootPageID() {
        return (root != null) ? root.getPageID() : null;
    }

    @Override
    public boolean isEmpty(Transaction<MBR<K>, V> tx) {
        return root == null;
    }

    @Override
    public boolean isOrdered() {
        // R-tree is not ordered
        return false;
    }

    @Override
    public TreeOperations<MBR<K>, V, RTreePage<K, V>> getOperations() {
        return operations;
    }

    /**
     * Requires the old root to be fixed (as it is always), and also the new
     * page. Will unfix the old root page and leave the new root fixed to page
     * buffer. Will also fix the root again (so that this method can unfix
     * it). Therefore, proper usage is: 1. fix/create new root page r, 2.
     * attachRoot(r), 3. release root page r.
     * 
     * @param page the new root. Must be fixed to buffer.
     */
    @Override
    protected void attachRoot(RTreePage<K, V> page, Transaction<MBR<K>, V> tx) {
        assert !page.isRoot() : String.format(
            "Trying to attach page %s which has already been attached", page.getName());

        if (this.root != null) {
            // Unfix old root from the buffer
            pageBuffer.unfix(root, tx);
        }
        this.root = page;
        pageBuffer.fixPage(page.getPageID(), pageFactory, false, tx);
        page.setRoot(true);
        // Update info page
        updateInfoPage(tx);
    }

    @Override
    protected void updateInfoPage(Owner owner) {
        RTreeInfoPage<K> infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, false, owner);
        // setRootPageID() sets the page dirty
        infoPage.setRootPageID(root != null ? root.getPageID() : PageID.INVALID_PAGE_ID);
        infoPage.setExtents(getExtents());
        pageBuffer.unfix(infoPage, owner);
    }

    @Override
    public String toString() {
        return String.format("R-Tree (h: %d), root: %s, extents: %s", getHeight(),
            (root != null ? root.getName() : "none"), getExtents());
    }

    /**
     * ACTION: Exact query
     */
    @Override
    public boolean getExact(MBR<K> key, Callback<Pair<MBR<K>, V>> callback, Owner owner) {
        log.debug("R-tree action: exact query " + key);
        return operations.findExact(key, callback, owner);
    }

    /**
     * ACTION: Overlap query
     */
    @Override
    public boolean getOverlapping(MBR<K> key, Callback<Pair<MBR<K>, V>> callback, Owner owner) {
        log.debug("R-tree action: overlap query " + key);
        return operations.findOverlapping(key, callback, owner);
    }

    @Override
    public void checkConsistency(Object... params) {
        log.info("Running consistency check");
        if (root != null) {
            // Check page fixes
            assert pageBuffer.getTotalPageFixes() == 1 : pageBuffer.getTotalPageFixes() + " != 1";
            root.checkConsistency();
            // Re-check page fixes
            assert pageBuffer.getTotalPageFixes() == 1 : pageBuffer.getTotalPageFixes() + " != 1";
        }
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
        System.out.println("Root: " + root);
    }

}
