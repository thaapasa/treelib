package fi.hut.cs.treelib.common;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.internal.TreeOperations;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.tuska.util.Bug;

public abstract class AbstractTreeOperations<K extends Key<K>, V extends PageValue<?>, P extends AbstractTreePage<K, V, P>>
    implements TreeOperations<K, V, P> {

    protected final PageBuffer buffer;
    protected final PageFactory<P> factory;
    private static final Logger log = Logger.getLogger(AbstractTreeOperations.class);

    private final AbstractTree<K, V, P> tree;
    private boolean overwriteEntries = false;
    protected final SMOPolicy smoPolicy;

    protected AbstractTreeOperations(AbstractTree<K, V, P> tree) {
        this.buffer = tree.getPageBuffer();
        this.factory = tree.getPageFactory();
        this.tree = tree;
        this.smoPolicy = tree.getDBConfig().getSMOPolicy();

        assert buffer != null;
        assert factory != null;
    }

    protected abstract Tree<K, V, P> getTree();

    public void setOverwriteEntries(boolean state) {
        this.overwriteEntries = state;
    }

    @Override
    public PagePath<K, V, P> findPathForInsert(PageID rootPageID, K key, PagePath<K, V, P> path,
        Transaction<K, V> tx) {
        return validatePathToLeafPage(rootPageID, key, path, tx);
    }

    /**
     * Finds the leaf page whose key range contains the given key in the given
     * tree version. All pages in the resulting path are fixed once by this
     * method and must be released by the caller (use unfixPath(path)).
     * 
     * Fix path so that it is the path to the appropriate leaf page
     */
    @Override
    public void findPathToLeafPage(PageID rootPageID, K key, PagePath<K, V, P> path, int version,
        Owner owner) {
        assert path.isEmpty();
        P page = buffer.fixPage(rootPageID, factory, false, owner);
        path.attachRoot(page);

        while (!page.isLeafPage()) {
            assert page.getKeyRange().contains(key) : String.format(
                "Index page %s does not contain key %s", page.getName(), key);

            PageID childID = page.findChildPointer(key);
            assert childID != null : String.format(
                "Index page %s does not contain child page with key %s", page.getName(), key);

            P child = buffer.fixPage(childID, factory, false, owner);

            if (path.isMaintainFullPath()) {
                path.descend(child);
            } else {
                // Traverse only using latch-coupling
                path.ascend();
                assert path.isEmpty();
                path.attachRoot(child);
                // Unfix previous page
                buffer.unfix(page, owner);
                page = null;
            }

            page = child;
        }
    }

    public void backtrackFindLeafPage(PageID rootPageID, K key, PagePath<K, V, P> path,
        int version, Owner owner) {
        assert !path.isEmpty();
        P page = path.getCurrent();
        assert page != null;

        while (!page.getKeyRange().contains(key)) {
            path.ascend();
            buffer.unfix(page, owner);
            page = path.getCurrent();
            assert page != null;
        }

        assert page.getKeyRange().contains(key);

        while (!page.isLeafPage()) {
            path.descend(key, owner);
            page = path.getCurrent();
        }
    }

    @Override
    public PagePath<K, V, P> validatePathToLeafPage(PageID rootPageID, K key,
        PagePath<K, V, P> path, Transaction<K, V> tx) {
        if (path == null) {
            // Default: maintain full path
            path = new PagePath<K, V, P>(true);
        }
        P leaf = path.getCurrent();
        if (path.isEmpty() || leaf == null || !leaf.isLeafPage()
            || !leaf.getKeyRange().contains(key)) {
            // Check if we are scanning forwards; if, then do a back-up up the
            // path and back down to correct leaf page; do this when
            // leaf.keyRange.max < key
            if (leaf != null && leaf.isLeafPage() && path.isMaintainFullPath()
                && leaf.getKeyRange().getMax().compareTo(key) < 0) {
                tree.getStatisticsLogger().log(Operation.OP_BACKTRACK_PATH);
                backtrackFindLeafPage(rootPageID, key, path, tx.getReadVersion(), tx);
                return path;
            } else {
                tree.getStatisticsLogger().log(
                    path.isEmpty() ? Operation.OP_TRAVERSE_PATH : Operation.OP_RETRAVERSE_PATH);
                // Path is not valid
                buffer.unfix(path, tx);
                findPathToLeafPage(rootPageID, key, path, tx.getReadVersion(), tx);
                return path;
            }
        }
        return path;
    }

    /**
     * Override to change behaviour.
     * 
     * @return true to allow duplicate entries.
     */
    protected boolean isAllowDuplicates() {
        return false;
    }

    /**
     * Inserts a key-value pair into this page, splitting the page if
     * necessary.
     * 
     * @return the page where the value was inserted to.
     */
    @Override
    public boolean insert(PagePath<K, V, P> path, K key, V value, Transaction<K, V> tx) {
        P page = path.getCurrent();
        assert page != null;
        assert page.isLeafPage();
        KeyRange<K> range = KeyRangeImpl.getKeyRange(key);
        // This is a leaf page

        // Check for existing entry with same key
        if (page.contains(key)) {
            // Auto-overwrite?
            if (overwriteEntries) {
                int oldC = page.getEntryCount();
                V old = page.removeEntry(key);
                page.putContents(range, value);
                int newC = page.getEntryCount();
                assert oldC == newC : oldC + " != " + newC;
                if (log.isDebugEnabled())
                    log.debug(String.format("Replaced key %s: %s -> %s", key, old, value));
                return true;
            }
            // Duplicates allowed?
            if (!isAllowDuplicates()) {
                if (log.isDebugEnabled())
                    log.debug(String.format(
                        "Not inserting key %s as it is already found in page %s", key, page
                            .getName()));

                return false;
            }
        }

        if (!smoPolicy.isAboutToOverflow(page)) {
            // Insert value into this page
            // page.putContents() sets the page dirty
            page.putContents(range, value);
            if (log.isDebugEnabled())
                log.debug(String.format("Leaf page %s was not full, added key %s",
                    page.getName(), key.toString()));

            return true;
        }

        // Page is about to overflow
        if (log.isDebugEnabled())
            log.debug(String.format("Page %s is full when inserting, need to split", page
                .getName()));

        // Split dirties the involved pages
        split(path, key, null, true, tx);
        // Proceed to insert to the new page
        return insert(path, key, value, tx);
    }

    @Override
    public void split(PagePath<K, V, P> path, K key, PageID childID,
        boolean checkUnderflowAfterSplit, Transaction<K, V> tx) {
        P page = path.getCurrent();
        if (!smoPolicy.isAboutToOverflow(page)) {
            // This may be required!
            log.debug(String.format(
                "Split() called even though page is not about to overflow (%d < %d)", page
                    .getEntryCount(), page.getPageEntryCapacity()));
        }
        if (log.isDebugEnabled())
            log.debug(String.format("Splitting page %s", page.getName()));

        if (page.isRoot(path)) {
            // Need to increase tree height - that solves the problem
            increaseTreeHeight(page, key, childID, path, tx);
            return;
        }

        int needSlots = countRequiredSpaceForSplit(path, tx);
        int iterations = 0;
        while (path.getParent().getFreeEntries() < needSlots) {
            if (++iterations > 10)
                throw new Bug("Too many iterations");

            // Need to split parent page so that there is space to add the
            // required pages
            P parent = path.ascend();
            if (log.isDebugEnabled())
                log.debug(String.format(
                    "Page %s's parent page %s is full when splitting; need %d slots, "
                        + "need to split it too", page.getName(), parent.getName(), needSlots));
            split(path, key, page.getPageID(), true, tx);
            parent = path.getCurrent();
            // assert parent.getFreeEntries() >= needSlots : needSlots + " < "
            // + parent.getFreeEntries() + " @" + parent;
            // Descend back to this page
            path.descend(page);
        }

        splitSpaceEnsured(page, key, childID, path, tx);
        return;
    }

    protected int countRequiredSpaceForSplit(PagePath<K, V, P> path, Transaction<K, V> tx) {
        // Default: splitting a page needs one new page slot
        return 1;
    }

    protected abstract void splitSpaceEnsured(P page, K key, PageID childID,
        PagePath<K, V, P> path, Transaction<K, V> tx);

    protected KeyRange<K> getKeyRange(K min, K max, P originatingPage, Transaction<K, V> tx) {
        return new KeyRangeImpl<K>(min, max);
    }

    protected void increaseTreeHeight(P oldRoot, K key, PageID childID, PagePath<K, V, P> path,
        Transaction<K, V> tx) {
        assert oldRoot == path.getCurrent();
        assert oldRoot.isRoot(path) : String.format(
            "Page %s is not root page, cannot increase height", oldRoot.getName());
        path.ascend();
        assert (path.isEmpty());

        // Dirties the old root
        oldRoot.setRoot(false);

        if (log.isDebugEnabled())
            log.debug(String.format("Increasing tree height from page %s", oldRoot.getName()));

        // Create new root page
        P newRoot = tree.createIndexRoot(oldRoot.getHeight() + 1, tx);

        // Move this page under the new root. The new root will contain only
        // one key with range [-INF, INF).
        KeyRange<K> oldRootRange = getKeyRange(tree.getKeyPrototype().getMinKey(), tree
            .getKeyPrototype().getMaxKey(), oldRoot, tx);
        newRoot.putContents(oldRootRange, oldRoot.getPageID());
        // setKeyRange dirties old root, new root is already dirty
        oldRoot.setKeyRange(oldRootRange);

        // The root is fixed for use, so it can be put into the path
        path.attachRoot(newRoot);
        path.descend(oldRoot);

        // Split this page (this page is no longer a root page)
        split(path, key, null, true, tx);
    }
}
