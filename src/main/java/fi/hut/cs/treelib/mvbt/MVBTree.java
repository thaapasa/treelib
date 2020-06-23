package fi.hut.cs.treelib.mvbt;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.OrderedTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.VisualizableTree;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.btree.BTreePage;
import fi.hut.cs.treelib.common.AbstractMVTree;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.SingleVersionTree;
import fi.hut.cs.treelib.common.TreeShortcuts;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.AssertionSupport;
import fi.tuska.util.Callback;
import fi.tuska.util.Holder;
import fi.tuska.util.NotImplementedException;
import fi.tuska.util.Pair;
import fi.tuska.util.Triple;
import fi.tuska.util.iterator.Iterables;

/**
 * @author thaapasa
 * 
 * @param <K>
 * @param <V>
 */
public class MVBTree<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMVTree<K, V, MVBTPage<K, V>> implements MVTree<K, V, MVBTPage<K, V>>,
    VisualizableTree<K, V, MVBTPage<K, V>>, OrderedTree<K, V, MVBTPage<K, V>> {

    private static final Logger log = Logger.getLogger(MVBTree.class);

    protected int activeVersion = 0;
    private final BTree<IntegerKey, PageID> roots;
    private final Transaction<IntegerKey, PageID> rootsTX;

    /** Current root kept cached. */
    private MVBTPage<K, V> currentRoot;

    /**
     * The range of key version space in the database, for visualization. Will
     * not shrink, only expand.
     */
    private MVKeyRange<K> range;

    private final MVBTOperations<K, V> operations;

    private final PageFactory<MVBTInfoPage<K>> infoPageFactory;

    protected MVBTree(PageID infoPageID, BTree<IntegerKey, PageID> rootTree,
        DatabaseConfiguration<K, V> dbConfig) {
        this("mvbt", "MVBT", infoPageID, rootTree, dbConfig);
    }

    public MVBTree(String identifier, String name, PageID infoPageID,
        BTree<IntegerKey, PageID> rootTree, DatabaseConfiguration<K, V> dbConfig) {
        super(identifier, name, infoPageID, dbConfig);
        this.currentRoot = null;
        this.roots = rootTree;
        this.rootsTX = new DummyTransaction<IntegerKey, PageID>("MVBT-roots");

        // Currently, a default range is forced. This could be changed, but is
        // it really worth it?
        this.range = new MVKeyRange<K>(keyPrototype.fromInt(0), 1, 2);

        this.infoPageFactory = new MVBTInfoPage<K>(dbConfig.getPageSize(), range);
        PageBuffer.registerPageFactory(infoPageFactory);

        initialize(new MVBTPageFactory<K, V>(this));

        // Must be after pageBuffer is initialized and pageFactory has been
        // created
        this.operations = new MVBTOperations<K, V>(this);
        loadTree();
    }

    protected BTree<IntegerKey, PageID> getRootStar() {
        return roots;
    }

    @Override
    public MVBTOperations<K, V> getOperations() {
        return operations;
    }

    public int getActiveVersion() {
        return activeVersion;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    /**
     * Call to suppress automatic page fix checking between operations.
     */
    @Override
    public void setCheckFixes(boolean checkFixes) {
        super.setCheckFixes(checkFixes);
        this.roots.setCheckFixes(checkFixes);
    }

    @Override
    protected void loadTree() {
        if (pageBuffer.reservePageID(infoPageID)) {
            // The info page was not reserved, so this is a new DB
            log.info("Initializing new MVB-tree DB information page");
        } else {
            log.info("Loading an existing MVB-tree DB information page");
        }
        MVBTInfoPage<K> infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, true,
            internalOwner);
        readInfoPage(infoPage);
        pageBuffer.unfix(infoPage, internalOwner);
    }

    protected void writeInfoPage(MVBTInfoPage<K> infoPage) {
        infoPage.setRootPageID(currentRoot != null ? currentRoot.getPageID()
            : PageID.INVALID_PAGE_ID);
        infoPage.setActiveVersion(getActiveVersion());
        infoPage.setKeyRange(range);
    }

    protected void readInfoPage(MVBTInfoPage<K> infoPage) {
        if (infoPage.getRootPageID().intValue() > 0) {
            // Load root page
            currentRoot = pageBuffer.fixPage(infoPage.getRootPageID(), pageFactory, false,
                internalOwner);
            assert currentRoot != null;
        }
        activeVersion = infoPage.getActiveVersion();
        range = infoPage.getKeyRange();
    }

    @Override
    public String toString() {
        return String.format("%s, version %d, root: %s", getName(), getActiveVersion(),
            currentRoot != null ? currentRoot.getName() : "none");
    }

    /**
     * ACTION: insert
     */
    @Override
    public boolean insert(final K key, final V value, PagePath<K, V, MVBTPage<K, V>> savedPath,
        Transaction<K, V> tx) {
        assert savedPath != null;
        // Advance version number
        int version = newAction(tx.getReadVersion(), tx);
        if (log.isDebugEnabled())
            log.debug("MVB-Tree action: insert " + key + ", version: " + version);
        if (currentRoot == null) {
            // Create new root for tree
            MVBTPage<K, V> newRoot = createLeafRoot(tx);
            pageBuffer.unfix(newRoot, tx);
        }
        assert currentRoot != null;

        savedPath = operations
            .validatePathToLeafPage(currentRoot.getPageID(), key, savedPath, tx);
        assert savedPath.isMaintainFullPath();

        if (savedPath.getCurrent().contains(key, tx)) {
            log.info("Tree contains key " + key + ", deleting it before insert");
            operations.delete(savedPath, key, tx);
            savedPath = operations.validatePathToLeafPage(currentRoot.getPageID(), key,
                savedPath, tx);
        }
        assert !savedPath.getCurrent().contains(key, tx) : String.format(
            "A version of key %s@%d found in tree after it was deleted", key, version);

        boolean success = operations.insert(savedPath, key, value, tx);

        if (success) {
            range = range.extend(key);
        }
        return success;
    }

    /**
     * ACTION: delete
     */
    @Override
    public boolean delete(final K key, PagePath<K, V, MVBTPage<K, V>> savedPath,
        Transaction<K, V> tx) {
        assert savedPath != null;
        if (currentRoot == null)
            return false;

        int version = newAction(tx.getReadVersion(), tx);
        assert version == tx.getReadVersion();

        if (log.isDebugEnabled())
            log.debug("MVB-Tree action: delete " + key + ", version: " + getActiveVersion());

        savedPath = operations
            .validatePathToLeafPage(currentRoot.getPageID(), key, savedPath, tx);
        assert savedPath.isMaintainFullPath();
        V value = operations.delete(savedPath, key, tx);

        return value != null;
    }

    @Override
    public void clearInternal(Transaction<K, V> tx) {
        log.debug("Clearing tree at version " + activeVersion);
        // Kill old root
        if (currentRoot != null) {
            if (currentRoot.getKeyRange().getMinVersion() != getActiveVersion()) {
                currentRoot.setKeyRange(currentRoot.getKeyRange().endVersionRange(activeVersion));
            }
            pageBuffer.unfix(currentRoot, tx);
            currentRoot = null;
        }
        // Insert invalid page id as the root id for this version
        TreeShortcuts.insert(roots, new IntegerKey(getActiveVersion()), PageID.INVALID_PAGE_ID,
            rootsTX);
        log.debug("Cleared tree");
    }

    @Override
    public int getHeight() {
        return currentRoot != null ? currentRoot.getHeight() : 0;
    }

    /**
     * Returns the current root (fixed), if the tree is not empty. Release
     * after use!
     */
    @Override
    public MVBTPage<K, V> getRoot(Owner owner) {
        if (currentRoot == null)
            return null;
        return pageBuffer.fixPage(currentRoot.getPageID(), pageFactory, false, owner);
    }

    @Override
    public PageID getRootPageID() {
        return (currentRoot != null) ? currentRoot.getPageID() : null;
    }

    /**
     * @param version the version whose root is queried
     * @return the root page ID of the given version; or null, if that version
     * has no root (the tree is empty)
     */
    public PageID getRootPageID(int version) {
        if (version == activeVersion) {
            return (currentRoot != null) ? currentRoot.getPageID() : null;
        }
        Pair<IntegerKey, PageID> entry = roots.floorEntry(new IntegerKey(version), rootsTX);
        if (entry == null || !entry.getSecond().isValid())
            return null;
        return entry.getSecond();
    }

    /**
     * Returns the fixed root page. Caller must unfix the root page after
     * usage!
     */
    @Override
    public MVBTPage<K, V> getRoot(int version, Owner owner) {
        if (version == activeVersion) {
            return getRoot(owner);
        }
        Pair<IntegerKey, PageID> entry = roots.floorEntry(new IntegerKey(version), rootsTX);
        if (entry == null || !entry.getSecond().isValid())
            return null;
        MVBTPage<K, V> page = pageBuffer.fixPage(entry.getSecond(), pageFactory, false, owner);
        return page;
    }

    /**
     * Performs whatever is necessary when a new action is performed.
     * 
     * @param requestedVersion the version that the transaction wants to use
     * @return the version number (changes in MVBT)
     */
    protected int newAction(int requestedVersion, Owner owner) {
        return newVersion(requestedVersion, owner);
    }

    protected final int newVersion(int requestedVersion, Owner owner) {
        assert activeVersion == requestedVersion;
        activeVersion++;
        log.debug(String.format("-- MVBTree, version %d --", activeVersion));
        range = range.extendVersion(activeVersion);
        updateInfoPage(owner);
        return activeVersion;
    }

    @Override
    protected void updateInfoPage(Owner owner) {
        MVBTInfoPage<K> infoPage = pageBuffer.fixPage(infoPageID, infoPageFactory, false, owner);
        writeInfoPage(infoPage);
        pageBuffer.unfix(infoPage, owner);
    }

    @Override
    public boolean isEmpty(Transaction<K, V> tx) {
        MVBTPage<K, V> root = getRoot(tx.getReadVersion(), tx);
        boolean empty = root == null;
        pageBuffer.unfix(root, tx);
        return empty;
    }

    /**
     * @param root the new root. Must have at least one fix. This method adds
     * a new fix which is reserved for releasing by this operation (when
     * replacing the root with a new one). Callers must release the fix(es)
     * they have acquired themselves.
     */
    @Override
    public void attachRoot(MVBTPage<K, V> root, Transaction<K, V> tx) {
        if (currentRoot != null && currentRoot.equals(root)) {
            // Already attached
            log.debug("Page " + root + " already attached, not reattaching");
            return;
        }
        log.debug(String.format("Attaching new root %s to tree at version %d", root.getName(),
            activeVersion));

        // Current root can be created at this version if it overflows and
        // tree height needs to be increased
        if (currentRoot != null && currentRoot.getKeyRange().getMinVersion() < activeVersion) {
            // Kill current root. setKeyRange() dirties the page
            currentRoot.setKeyRange(currentRoot.getKeyRange().endVersionRange(activeVersion));
            // Check: no alive entries left in root
            if (AssertionSupport.isAssertionsEnabled()) {
                if (currentRoot.isLeafPage()) {
                    for (Triple<MVKeyRange<K>, Boolean, V> e : Iterables.get(currentRoot
                        .getLeafEntries().logicalIterator())) {
                        assert !currentRoot.isActive(e.getFirst());
                    }
                } else {
                    for (MVKeyRange<K> range : currentRoot.getContents().keySet()) {
                        assert !currentRoot.isActive(range) : range;
                    }
                }
            }
        }

        if (currentRoot != null) {
            // Unfix old root
            pageBuffer.unfix(currentRoot, tx);
        }

        pageBuffer.fixPage(root.getPageID(), pageFactory, false, tx);
        currentRoot = root;
        IntegerKey vKey = new IntegerKey(activeVersion);
        if (roots.contains(vKey, rootsTX)) {
            TreeShortcuts.delete(roots, vKey, rootsTX);
        }
        boolean success = TreeShortcuts
            .insert(this.roots, vKey, currentRoot.getPageID(), rootsTX);
        assert success;
        updateInfoPage(tx);
    }

    @Override
    public Tree<K, V, MVBTPage<K, V>> getVersionTree(int version) {
        if (version > getActiveVersion() || version < 0) {
            return null;
        }
        return new SingleVersionTree<K, V, MVBTPage<K, V>>(this, version);
    }

    /**
     * Forces the tree contents into the underlying storage.
     */
    @Override
    public void flush() {
        super.flush();
        roots.flush();
    }

    @Override
    public MVKeyRange<K> getKeyRange() {
        return range;
    }

    @Override
    public int getMaxHeight() {
        int maxHeight = 0;
        for (Pair<IntegerKey, PageID> entry : TreeShortcuts.getRange(roots, range
            .getVersionRange().getEntireKeyRange(), rootsTX)) {
            PageID rootID = entry.getSecond();
            MVBTPage<K, V> root = pageBuffer.fixPage(rootID, pageFactory, false, internalOwner);
            if (root != null && root.getHeight() > maxHeight) {
                maxHeight = root.getHeight();
            }
            pageBuffer.unfix(root, internalOwner);
        }
        return maxHeight;
    }

    @Override
    public Collection<VisualizablePage<K, V>> getPagesAtHeight(int height) {
        SortedSet<VisualizablePage<K, V>> pages = new TreeSet<VisualizablePage<K, V>>();
        for (Pair<IntegerKey, PageID> entry : TreeShortcuts.getRange(roots, range
            .getVersionRange().getEntireKeyRange(), rootsTX)) {
            PageID rootID = entry.getSecond();
            MVBTPage<K, V> root = pageBuffer.fixPage(rootID, pageFactory, false, internalOwner);
            if (root != null) {
                root.collectPagesAtHeight(height, pages, internalOwner);
            }
            pageBuffer.unfix(root, internalOwner);
        }
        return pages;
    }

    @Override
    public int getHeight(int version) {
        MVBTPage<K, V> root = getRoot(version, internalOwner);
        int height = root != null ? root.getHeight() : 0;
        pageBuffer.unfix(root, internalOwner);
        return height;
    }

    protected Set<Integer> getSeparateRootedVersions() {
        List<Pair<IntegerKey, PageID>> list = TreeShortcuts.getRange(roots, range
            .getVersionRange().getEntireKeyRange(), rootsTX);
        Set<Integer> versions = new HashSet<Integer>(list.size());
        for (Pair<IntegerKey, PageID> entry : list) {
            versions.add(entry.getFirst().intValue());
        }
        return versions;
    }

    @Override
    public void printDebugInfo() {
        System.out.println("Current root: " + (currentRoot != null ? currentRoot : "none"));
        System.out
            .println("Roots: "
                + TreeShortcuts.getRange(roots, range.getVersionRange().getEntireKeyRange(),
                    rootsTX));
        System.out.println("KeyRange: " + range);
        long pages = TreeShortcuts.countMVPages(this, true);
        System.out.println(getName() + " size (pages): " + pages);

        System.out.println("Root*:");
        roots.printDebugInfo();
    }

    private void browseVersions(Callback<Pair<IntegerKey, PageID>> callback,
        KeyRange<IntegerKey> versions, Owner owner) {
        int startV = versions.getMin().intValue();
        int endV = versions.getMax().intValue();

        PagePath<IntegerKey, PageID, BTreePage<IntegerKey, PageID>> savedPath = new PagePath<IntegerKey, PageID, BTreePage<IntegerKey, PageID>>(
            false);

        // Start by looking for the floor entry based on the starting version
        Pair<IntegerKey, PageID> root = roots.floorEntry(new IntegerKey(startV), rootsTX);
        if (root == null) {
            root = roots.nextEntry(new IntegerKey(startV), savedPath, rootsTX);
        }
        if (root == null) {
            // No entries in roots
            assert roots.isEmpty(rootsTX);
            pageBuffer.unfix(savedPath, owner);
            return;
        }
        IntegerKey cur = root.getFirst();
        // Loop until we have gone past the ending version
        while (cur.intValue() < endV) {
            assert versions.contains(root.getFirst());

            // If the tree has been emptied then there can be an ampty marker
            // in the root* (pageID is null or invalid)
            // Currently, do not call callback for those entries
            if (root.getSecond() != null && root.getSecond().isValid()) {
                callback.callback(root);
            }

            // Get the next version from the root*
            root = roots.nextEntry(cur, savedPath, rootsTX);
            if (root == null) {
                // No more versions stored in the root*
                break;
            }
            cur = root.getFirst();
        }
        // All versions smaller than endV have been traversed
        pageBuffer.unfix(savedPath, owner);
    }

    @Override
    public void traverseMVPages(final Predicate<MVKeyRange<K>> predicate,
        final Callback<Page<K, V>> operation, final Owner owner) {
        MVKeyRange<K> searchedRange = predicate.getRestrictedRange();
        // Browse through the versions in root* contained in the restricted
        // range
        browseVersions(new Callback<Pair<IntegerKey, PageID>>() {
            @Override
            public boolean callback(Pair<IntegerKey, PageID> version) {
                traversePagesInVersion(predicate, operation, version.getFirst().intValue(), owner);
                return true;
            }
        }, searchedRange.getVersionRange(), owner);
    }

    private void traversePagesInVersion(Predicate<MVKeyRange<K>> predicate,
        Callback<Page<K, V>> operation, int version, Owner owner) {
        MVBTPage<K, V> root = getRoot(version, owner);
        if (root == null) {
            // Tree empty in this version
            return;
        }
        root.traverseMVPages(predicate, operation, owner);
        pageBuffer.unfix(root, owner);
    }

    @Override
    public boolean isMultiVersion() {
        return true;
    }

    @Override
    public Pair<K, V> floorEntry(K key, Transaction<K, V> tx) {
        throw new NotImplementedException();
    }

    @Override
    public Pair<K, V> nextEntry(final K key, PagePath<K, V, MVBTPage<K, V>> savedPath,
        Transaction<K, V> tx) {
        assert savedPath != null;
        KeyRange<K> searchRange = new KeyRangeImpl<K>(key.nextKey(), key.getMaxKey());
        final Holder<Pair<K, V>> nextEntry = new Holder<Pair<K, V>>();

        // Find root page ID (from saved path, if that has been initialized)
        PageID rootID = savedPath.isEmpty() ? getRootPageID(tx.getReadVersion()) : savedPath
            .getRoot().getPageID();

        if (rootID != null) {
            // Check that path is correct
            savedPath = operations.validatePathToLeafPage(rootID, key, savedPath, tx);

            // Scan through the entries using the saved path
            operations.getRange(rootID, searchRange, new Callback<Pair<K, V>>() {
                @Override
                public boolean callback(Pair<K, V> entry) {
                    assert entry.getFirst().compareTo(key) > 0;
                    nextEntry.setValue(entry);
                    // Return false to stop search
                    return false;
                }
            }, savedPath, tx);
        }
        return nextEntry.getValue();
    }

    @Override
    public void setStatisticsLogger(StatisticsLogger stats) {
        super.setStatisticsLogger(stats);
        roots.setStatisticsLogger(stats);
    }

    @Override
    public int getCommittedVersion() {
        return activeVersion;
    }

    @Override
    public int getLatestVersion() {
        return getActiveVersion();
    }

}
