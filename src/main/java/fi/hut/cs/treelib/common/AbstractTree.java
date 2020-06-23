package fi.hut.cs.treelib.common;

import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.internal.CountEntriesOperation;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.stats.NoStatistics;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.hut.cs.treelib.util.KeyRangePredicate;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.AssertionSupport;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

/**
 * Contains basic functionality required for all sorts of trees. This includes
 * references to page buffer and page factory, statistics logger, key and
 * value prototypes, etc.
 * 
 * @author thaapasa
 * 
 * @param <K> tree key type
 * @param <V> tree value type
 * @param <P> page type
 */
public abstract class AbstractTree<K extends Key<K>, V extends PageValue<?>, P extends AbstractTreePage<K, V, P>>
    implements Tree<K, V, P>, Component {

    private static final Logger log = Logger.getLogger(AbstractTree.class);

    public static final int DEFAULT_READ_VERSION = 0;
    public static final int DEFAULT_TRANSACTION_ID = -1;

    protected DatabaseConfiguration<K, V> dbConfig;
    protected PageBuffer pageBuffer;
    protected PageFactory<P> pageFactory;
    private KeyRange<K> keyRange;

    protected final K keyPrototype;
    protected final V valuePrototype;

    /** The PageID of the tree info page (in page buffer). */
    protected final PageID infoPageID;

    protected StatisticsLogger stats = NoStatistics.instance();

    private final String identifier;
    private final String name;
    private int defaultBufferFixesAfterActions = 1;
    private boolean checkFixes = true;

    public final Owner internalOwner;

    protected AbstractTree(String identifier, String name, PageID infoPageID,
        DatabaseConfiguration<K, V> dbConfig) {
        this.dbConfig = dbConfig;
        this.identifier = identifier;
        this.name = name;
        this.infoPageID = infoPageID;

        this.keyPrototype = dbConfig.getKeyPrototype();
        this.valuePrototype = dbConfig.getValuePrototype();
        assert keyPrototype != null;
        assert valuePrototype != null;

        this.pageBuffer = dbConfig.getPageBuffer();
        this.internalOwner = new OwnerImpl(identifier + "-internal");

        assert keyPrototype != null;
        assert valuePrototype != null;
    }

    public void close() {
        dbConfig = null;
        pageBuffer = null;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public final String getName() {
        return name;
    }

    protected void initialize(PageFactory<P> pageFactory) {
        this.pageFactory = pageFactory;
        if (!this.pageBuffer.isInitialized()) {
            this.pageBuffer.initialize();
        }
        PageBuffer.registerPageFactory(pageFactory);
    }

    @Override
    public PageFactory<P> getPageFactory() {
        return pageFactory;
    }

    @Override
    public PageBuffer getPageBuffer() {
        return pageBuffer;
    }

    public DatabaseConfiguration<K, V> getDBConfig() {
        return dbConfig;
    }

    /**
     * Some implementations have more than one page fixed between actions.
     * Call this to update the expected value.
     */
    public void setDefaultBufferFixesAfterActions(int defaultBufferFixesAfterActions) {
        this.defaultBufferFixesAfterActions = defaultBufferFixesAfterActions;
    }

    /**
     * Call to suppress automatic page fix checking between operations.
     */
    public void setCheckFixes(boolean checkFixes) {
        this.checkFixes = checkFixes;
    }

    /**
     * @return the new page, fixed to buffer
     */
    public P createSiblingPage(P page, Owner owner) {
        P sibling = pageBuffer.createPage(pageFactory, owner);
        sibling.format(page.getHeight());
        sibling.setKeyRange(page.getKeyRange());
        return sibling;
    }

    /**
     * ACTION: Inserts a value into the tree.
     */
    @Override
    public boolean insert(K key, V value, PagePath<K, V, P> savedPath, Transaction<K, V> tx) {
        if (log.isDebugEnabled()) {
            log.debug(getName() + " action: insert" + key);
        }
        assert savedPath != null;

        P root = getRoot(tx);
        if (root == null) {
            // createLeafRoot() returns a fixed page
            P newRoot = createLeafRoot(tx);
            // ... root is therefore now fixed
            root = newRoot;
            assert root != null;
        }
        PageID rootID = root.getPageID();
        // Release old root (getRoot() left an extra fix)
        pageBuffer.unfix(root, tx);

        extendKeyRange(key);
        // Unlog the extra fix from getRoot() (findPathForInsert will take a
        // new fix for the root page)
        stats.unlog(Operation.OP_BUFFER_FIX);
        savedPath = getOperations().findPathForInsert(rootID, key, savedPath, tx);
        boolean result = getOperations().insert(savedPath, key, value, tx);
        runAtInsert(savedPath, key);

        // pageBuffer.printPageFixes();
        return result;
    }

    /**
     * Call to attach a new root to the tree. This will detach the old root.
     */
    abstract protected void attachRoot(P rootPage, Transaction<K, V> tx);

    /**
     * Called to create the first root of the tree (a leaf page).
     * 
     * @return the root, fixed. Release after use!
     */
    public P createLeafRoot(Transaction<K, V> tx) {
        // Returns a new page with one fix
        P newRoot = pageBuffer.createPage(pageFactory, tx);
        newRoot.format(1);
        // Attach the new root. The root is left fixed to the page buffer.
        attachRoot(newRoot, tx);
        if (log.isDebugEnabled()) {
            log.debug(String.format("Created leaf root page %s", newRoot));
        }
        return newRoot;
    }

    /**
     * Called to create an index root for the tree (when increasing tree
     * height).
     * 
     * @return the root, fixed. Release after use!
     */
    public P createIndexRoot(int height, Transaction<K, V> tx) {
        // P oldRoot = getRoot();
        // assert oldRoot != null;

        P newRoot = pageBuffer.createPage(pageFactory, tx);
        newRoot.format(height);

        // Attach the new root. The root is left fixed to the page buffer.
        attachRoot(newRoot, tx);
        if (log.isDebugEnabled()) {
            log.debug(String.format("Created index root page %s", newRoot));
        }
        return newRoot;
    }

    public StatisticsLogger getStatisticsLogger() {
        return stats;
    }

    public void setStatisticsLogger(StatisticsLogger stats) {
        assert stats != null;
        this.stats = stats;
        this.pageBuffer.setStatisticsLogger(stats);
    }

    protected void extendKeyRange(K key) {
        assert key != null;
        keyRange = keyRange != null ? keyRange.extend(key) : KeyRangeImpl.getKeyRange(key);
    }

    @Override
    public KeyRange<K> getKeyRange() {
        return keyRange != null ? keyRange : new KeyRangeImpl<K>(keyPrototype.getMinKey(),
            keyPrototype.getMaxKey());
    }

    public K getKeyPrototype() {
        return keyPrototype;
    }

    public V getValuePrototype() {
        return valuePrototype;
    }

    /**
     * Forces the tree contents into the underlying storage.
     */
    @Override
    public void flush() {
        pageBuffer.flush(true);
    }

    /**
     * Called after each user action (insert, delete, get, getRange, delete,
     * clear).
     * 
     * Remember to add call to this after each user-triggered action!
     */
    public void runAfterAction() {
        // Default operations:
        // Check consistency, if configuration says so
        if (Configuration.instance().isCheckConsistency()) {
            checkConsistency();
        }
        if (checkFixes) {
            int fixP = pageBuffer.getTotalPageFixes();
            assert fixP >= 0 && fixP <= defaultBufferFixesAfterActions : fixP
                + " fixes (allowed " + defaultBufferFixesAfterActions + "): " + pageBuffer
                + " , fixes: " + pageBuffer.getPageFixSummary();
        }
        // Flush page buffer to disk, if configuration says so
        if (Configuration.instance().isFlushPagesAfterActions()) {
            log.debug("Flushing all pages to disk");
            pageBuffer.flush(true);
            if (checkFixes) {
                int bufP = pageBuffer.getPagesInBuffer();
                assert bufP >= 0 && bufP <= defaultBufferFixesAfterActions : bufP + " pages: "
                    + pageBuffer + ", fixes: " + pageBuffer.getPageFixSummary();
            }
        }
    }

    /**
     * Override to do some action right after insert has been performed; that
     * is, before the pages at the page path are released.
     */
    protected void runAtInsert(PagePath<K, V, P> path, K key) {
    }

    @Override
    public int countAllEntries() {
        P root = getRoot(internalOwner);
        if (root == null)
            return 0;
        CountEntriesOperation<K, V> op = new CountEntriesOperation<K, V>(this);
        KeyRangePredicate<K> pred = new KeyRangePredicate<K>();
        pred.setOnlyHeight(1);
        root.traversePages(pred, op, internalOwner);
        pageBuffer.unfix(root, internalOwner);
        return op.getEntryCount();
    }

    public void traversePages(final Predicate<KeyRange<K>> predicate,
        final Callback<Page<K, V>> operation, Owner owner) {
        P root = getRoot(owner);
        if (root == null)
            return;

        root.traversePages(predicate, operation, owner);
        pageBuffer.unfix(root, owner);
    }

    public abstract void deleteRoot(P root, Owner owner);

    protected abstract void loadTree();

    protected abstract void updateInfoPage(Owner owner);

    protected void extendRangeBulkLoad(K key) {
        this.keyRange = keyRange.extend(key);
    }

    /**
     * Bulk-loads a set of keys. Creates a new tree structure. Returns the
     * root page of the newly built tree, fixed. All other pages are unfixed.
     * The root is not attached, so call attachRoot() after bulkLoad() to
     * attach the newly created tree structure to the tree itself.
     * 
     * @param keys the keys to load (must be in proper order)
     * @return the unattached root of the new tree, with one fix (release it!)
     */
    public P bulkLoad(Iterable<Pair<K, V>> keys, Comparator<Pair<K, V>> entryComparator) {
        Deque<PageID> pages = new LinkedList<PageID>();
        createLeafPages(keys, pages, entryComparator);

        int level = 1;
        while (pages.size() > 1) {
            // Create next level
            level++;
            Deque<PageID> nextLevel = new LinkedList<PageID>();

            P cur = null;
            for (PageID pageID : pages) {
                P page = pageBuffer.fixPage(pageID, pageFactory, false, internalOwner);
                if (cur == null || cur.isFull()) {
                    P prevPage = cur;
                    pageBuffer.unfix(cur, internalOwner);
                    cur = pageBuffer.createPage(pageFactory, internalOwner);
                    cur.format(level);
                    nextLevel.addLast(cur.getPageID());
                    if (prevPage != null)
                        checkPageLimits(prevPage, cur, null, false);
                }
                assert page != null : "Page " + pageID + " not found";
                cur.attachChild(page);
                pageBuffer.unfix(page, internalOwner);
            }
            checkPageLimits(cur, null, null, true);
            pageBuffer.unfix(cur, internalOwner);
            pages = nextLevel;
        }
        PageID newRootID = pages.getFirst();
        assert newRootID != null;
        P newRoot = pageBuffer.fixPage(newRootID, pageFactory, false, internalOwner);
        assert newRoot != null;
        return newRoot;
    }

    private void createLeafPages(Iterable<Pair<K, V>> keys, Deque<PageID> pages,
        Comparator<Pair<K, V>> entryComparator) {
        double targetFillRatio = Configuration.instance().getBulkLoadFillRatio();
        Pair<K, V> prev = null;
        P curPage = null;
        for (Pair<K, V> entry : keys) {
            K key = entry.getFirst();
            V value = entry.getSecond();
            if (isOrdered()) {
                // Check that incoming keys are in proper order
                if (prev != null) {
                    if (entryComparator != null ? entryComparator.compare(prev, entry) > 0 : prev
                        .getFirst().compareTo(entry.getFirst()) > 0)
                        throw new IllegalArgumentException("Keys are not in correct order: "
                            + prev + " > " + key);
                }
            }

            if (curPage == null || curPage.isFull() || curPage.getFillRatio() >= targetFillRatio) {
                P lastPage = curPage;
                pageBuffer.unfix(curPage, internalOwner);
                curPage = pageBuffer.createPage(pageFactory, internalOwner);
                curPage.format(1);
                pages.addLast(curPage.getPageID());
                if (lastPage != null)
                    checkPageLimits(lastPage, curPage, key, false);
            }
            assert !curPage.isFull();
            curPage.putContents(key, value);
            extendRangeBulkLoad(key);
            prev = entry;
        }
        checkPageLimits(curPage, null, null, true);
        pageBuffer.unfix(curPage, internalOwner);
    }

    protected void checkPageLimits(P prevPage, P newPage, K key, boolean lastPageAtThisLevel) {
        // Nothing needed here
    }

    @Override
    public void checkConsistency(Object... params) {
        if (!AssertionSupport.isAssertionsEnabled()) {
            // Do nothing if assertions are not enabled
            return;
        }
        boolean forceCheck = false;
        if (params.length > 0 && params[0] instanceof Boolean) {
            forceCheck = (Boolean) params[0];
        }
        boolean doRecurse = forceCheck || Configuration.instance().isCheckConsistency();

        P currentRoot = getRoot(internalOwner);

        if (log.isDebugEnabled())
            log.debug("Checking consistency of " + getName() + " from root page "
                + currentRoot.getName() + "; recurse: " + doRecurse);

        if (currentRoot == null) {
            // Empty tree is consistent
            return;
        }
        if (doRecurse) {
            // Root is fixed
            PagePath<K, V, P> path = new PagePath<K, V, P>(currentRoot, true);
            currentRoot.checkConsistency(path);
            // Release the fix on the entire path
            pageBuffer.unfix(path, internalOwner);
        } else {
            // Release the fix on root
            pageBuffer.unfix(currentRoot, internalOwner);
        }
    }
}
