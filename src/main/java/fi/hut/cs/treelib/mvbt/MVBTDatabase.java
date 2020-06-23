package fi.hut.cs.treelib.mvbt;

import java.util.Set;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVDatabase;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.common.AbstractDatabase;
import fi.hut.cs.treelib.common.DatabaseConfigurationImpl;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.NonThrashingSMOPolicy;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.concurrency.NoopLatchManager;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;

public class MVBTDatabase<K extends Key<K>, V extends PageValue<?>> extends
    AbstractDatabase<K, V, MVBTPage<K, V>> implements MVDatabase<K, V, MVBTPage<K, V>> {

    protected static final PageID INFO_PAGE_ID = new PageID(1);
    protected static final PageID ROOT_INFO_PAGE_ID = new PageID(2);

    private static final SMOPolicy ROOT_TREE_POLICY = new NonThrashingSMOPolicy(0.2, 0.2);

    public MVBTDatabase(int bufferSize, SMOPolicy smoPolicy, K keyPrototype, V valuePrototype,
        PageStorage pageStorage) {
        super(bufferSize, pageStorage, smoPolicy, NoopLatchManager.instance(), keyPrototype,
            valuePrototype);

        initStructures();
    }

    @Override
    protected void initStructures() {
        // Tree cleared in AbstractDatabase.clear()

        PageBuffer pageBuffer = getPageBuffer();
        pageBuffer.reservePageID(INFO_PAGE_ID);
        pageBuffer.reservePageID(ROOT_INFO_PAGE_ID);

        initialize(createBackingTree());
    }

    @Override
    protected void clearStructures() {
        // Tree cleared in AbstractDatabase.close()
    }

    protected BTree<IntegerKey, PageID> createRootTree() {
        BTree<IntegerKey, PageID> rootTree = new BTree<IntegerKey, PageID>(ROOT_INFO_PAGE_ID,
            new DatabaseConfigurationImpl<IntegerKey, PageID>(this, IntegerKey.PROTOTYPE,
                PageID.PROTOTYPE, ROOT_TREE_POLICY));
        return rootTree;
    }

    protected MVBTree<K, V> createBackingTree() {
        BTree<IntegerKey, PageID> rootTree = createRootTree();

        MVBTree<K, V> tree = new MVBTree<K, V>(INFO_PAGE_ID, rootTree, this);

        return tree;
    }

    protected void initialize(MVBTree<K, V> tree) {
        // There can be two fixes between actions: one for the root of the
        // MVBT, and one for the root of the root*
        tree.setDefaultBufferFixesAfterActions(2);
        tree.getRootStar().setDefaultBufferFixesAfterActions(2);
        tree.getRootStar().setCheckFixes(false);

        super.initialize(tree);
    }

    protected static PageBuffer createPageBuffer(PageStorage pageStorage, int bufferSize) {
        // Create the page buffer, and reserve the info pages
        PageBuffer pageBuffer = new PageBuffer(pageStorage, bufferSize, NoopLatchManager
            .instance());
        pageBuffer.initialize();
        pageBuffer.reservePageID(INFO_PAGE_ID);
        pageBuffer.reservePageID(ROOT_INFO_PAGE_ID);
        return pageBuffer;
    }

    @Override
    public Transaction<K, V> beginTransaction() {
        return new MVBTTransaction<K, V>(this, getCommittedVersion(), false);
    }

    @Override
    public Transaction<K, V> beginReadTransaction(int version) {
        return new MVBTTransaction<K, V>(this, version, true);
    }

    @Override
    public MVBTree<K, V> getDatabaseTree() {
        return (MVBTree<K, V>) super.getDatabaseTree();
    }

    @Override
    public MVBTDatabase<K, V> getMVDatabase() {
        return this;
    }

    @Override
    public boolean isMultiVersion() {
        return true;
    }

    @Override
    public int getCommittedVersion() {
        return getDatabaseTree().getCommittedVersion();
    }

    public int getActiveVersion() {
        return getDatabaseTree().getActiveVersion();
    }

    @Override
    public Set<Integer> getSeparateRootedVersions() {
        return getDatabaseTree().getSeparateRootedVersions();
    }

    @Override
    public void traversePages(Predicate<KeyRange<K>> predicate, Callback<Page<K, V>> operation,
        Owner owner) {
        tree.traversePages(predicate, operation, owner);
    }

    @Override
    public boolean isMultiDimension() {
        return false;
    }

    /**
     * Default implementation: Do nothing, just return a dummy value.
     */
    @Override
    public int commit(Transaction<K, V> tx) {
        return tx.getReadVersion();
    }

    @Override
    public int getLatestVersion() {
        return getActiveVersion();
    }

}
