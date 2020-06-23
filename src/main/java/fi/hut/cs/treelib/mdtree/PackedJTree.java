package fi.hut.cs.treelib.mdtree;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Coordinate;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.AbstractMDTree;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.internal.TreeOperations;
import fi.hut.cs.treelib.mdtree.OMDTreeOperations.SplitType;
import fi.hut.cs.treelib.stats.NoStatistics;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.hut.cs.treelib.util.MBRPredicate;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.Counter;
import fi.tuska.util.Holder;
import fi.tuska.util.NotImplementedException;
import fi.tuska.util.Pair;

public class PackedJTree<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMDTree<K, V, Coordinate<K>, OMDPage<K, V, Coordinate<K>>> implements
    MDTree<K, V, OMDPage<K, V, Coordinate<K>>>, Component {

    private static boolean CHECK_ITEM_ORDERING = false;

    private static final Logger log = Logger.getLogger(PackedJTree.class);
    private final JTree<K, IntegerValue> rootTree;
    protected final Transaction<MBR<K>, IntegerValue> rootsTX;
    private final PageBuffer pageBuffer;
    private final SplitType splitType;

    private final Predicate<MBR<K>> mbrAllPred;
    private StatisticsLogger noActionLogger = NoStatistics.instance();

    protected PackedJTree(JTree<K, IntegerValue> rootTree, SplitType splitType,
        DatabaseConfiguration<MBR<K>, V> dbConfig) {
        super("packed-jtree", "Packed J-tree tree wrapper", PageID.INVALID_PAGE_ID, dbConfig);

        this.mbrAllPred = new MBRPredicate<K>();
        this.pageBuffer = dbConfig.getPageBuffer();
        this.rootTree = rootTree;
        this.rootsTX = new DummyTransaction<MBR<K>, IntegerValue>(internalOwner);
        this.splitType = splitType;
    }

    @Override
    public MBR<K> getExtents() {
        return rootTree.getExtents();
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public void setStatisticsLogger(StatisticsLogger stats) {
        super.setStatisticsLogger(stats);
    }

    private JTree<K, V> getExactTree(MBR<K> key) {
        Pair<MBR<K>, IntegerValue> entry = rootTree.floorEntry(key, rootsTX);
        IntegerValue val = entry.getSecond();

        return getExactTree(val);
    }

    private JTree<K, V> getExactTree(IntegerValue rootPageVal) {
        PageID rootPageID = new PageID(rootPageVal);
        stats.log(Operation.OP_SUBTREE_TRAVERSED);
        JTree<K, V> tree = new JTree<K, V>(splitType, rootPageID, dbConfig);
        tree.setStatisticsLogger(noActionLogger);
        // Allow for one extra fix from the packed root tree root page
        tree.setDefaultBufferFixesAfterActions(2);
        // Unlog the tree creation root fix
        stats.unlog(Operation.OP_BUFFER_FIX);
        return tree;
    }

    @Override
    public boolean getExact(MBR<K> key, Callback<Pair<MBR<K>, V>> callback, Owner owner) {
        JTree<K, V> tree = getExactTree(key);
        boolean res = tree.getExact(key, callback, owner);
        tree.close();
        return res;
    }

    @Override
    public boolean getOverlapping(final MBR<K> mbr, final Callback<Pair<MBR<K>, V>> callback,
        final Owner owner) {
        if (log.isDebugEnabled()) {
            log.debug(getName() + " operation: get overlapping");
        }
        MBRPredicate<K> pred = new MBRPredicate<K>();
        pred.setSearchMBR(mbr);
        return traverseTrees(pred, new Callback<JTree<K, V>>() {
            @Override
            public boolean callback(JTree<K, V> tree) {
                tree.getOverlappingInternal(mbr, callback, false, owner);
                // True to continue search
                return true;
            }
        }, owner);
    }

    @Override
    public boolean contains(MBR<K> key, Transaction<MBR<K>, V> tx) {
        JTree<K, V> tree = getExactTree(key);
        boolean result = tree.contains(key, tx);
        tree.close();
        return result;
    }

    @Override
    public int countAllEntries() {
        final Counter entries = new Counter();
        traverseTrees(new Callback<JTree<K, V>>() {
            @Override
            public boolean callback(JTree<K, V> tree) {
                int count = tree.countAllEntries();
                entries.advance(count);
                // True to continue search
                return true;
            }
        }, internalOwner);
        return (int) entries.getCount();
    }

    @Override
    public boolean delete(MBR<K> key,
        PagePath<MBR<K>, V, OMDPage<K, V, Coordinate<K>>> savedPath, Transaction<MBR<K>, V> tx) {
        JTree<K, V> tree = getExactTree(key);
        boolean result = tree.delete(key, savedPath, tx);
        tree.close();
        return result;
    }

    @Override
    public boolean insert(MBR<K> key, V value,
        PagePath<MBR<K>, V, OMDPage<K, V, Coordinate<K>>> savedPath, Transaction<MBR<K>, V> tx) {
        JTree<K, V> tree = getExactTree(key);
        boolean result = tree.insert(key, value, savedPath, tx);
        tree.close();
        return result;
    }

    @Override
    public V get(MBR<K> key, Transaction<MBR<K>, V> tx) {
        JTree<K, V> tree = getExactTree(key);
        V result = tree.get(key, tx);
        tree.close();
        return result;
    }

    @Override
    public int getHeight() {
        // TODO: This is not a very interesting value for this tree
        return rootTree.getHeight();
    }

    @Override
    public KeyRange<MBR<K>> getKeyRange() {
        throw new NotImplementedException();
    }

    @Override
    public int getMaxHeight() {
        throw new NotImplementedException();
    }

    @Override
    public TreeOperations<MBR<K>, V, OMDPage<K, V, Coordinate<K>>> getOperations() {
        throw new NotImplementedException();
    }

    @Override
    public OMDPage<K, V, Coordinate<K>> getPage(MBR<K> key, Owner owner) {
        throw new NotImplementedException();
    }

    @Override
    public PageBuffer getPageBuffer() {
        return pageBuffer;
    }

    @Override
    public PageFactory<OMDPage<K, V, Coordinate<K>>> getPageFactory() {
        // Faked tree-factory for visualization
        return new OMDPageFactory<K, V, Coordinate<K>>(this, JTree
            .createSearchKeyCreator(getKeyPrototype()));
    }

    @Override
    public OMDPage<K, V, Coordinate<K>> getRoot(Owner owner) {
        // This is called from the inspect operation, but that doesn't matter
        throw new UnsupportedOperationException();
    }

    @Override
    public PageID getRootPageID() {
        throw new UnsupportedOperationException();
    }

    private void traverseTrees(final Callback<JTree<K, V>> callback, Owner owner) {
        traverseTrees(mbrAllPred, callback, owner);
    }

    private boolean traverseTrees(Predicate<MBR<K>> predicate,
        final Callback<JTree<K, V>> callback, Owner owner) {
        if (rootTree == null)
            return true;

        return rootTree.traverseMDEntries(predicate, new Callback<Pair<MBR<K>, PageValue<?>>>() {
            @Override
            public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                IntegerValue infoID = (IntegerValue) entry.getSecond();
                JTree<K, V> jtree = getExactTree(infoID);
                boolean res = callback.callback(jtree);
                jtree.close();
                return res;
            }
        }, owner);
    }

    @Override
    public boolean traverseMDPages(final Predicate<MBR<K>> predicate,
        final Callback<Page<MBR<K>, V>> operation, final Owner owner) {

        return traverseTrees(predicate, new Callback<JTree<K, V>>() {
            @Override
            public boolean callback(JTree<K, V> tree) {
                tree.traverseMDPages(predicate, operation, owner);
                // True to continue search
                return true;
            }
        }, owner);
    }

    @Override
    public boolean isEmpty(final Transaction<MBR<K>, V> tx) {
        if (rootTree.isEmpty(rootsTX))
            return true;

        final Holder<Boolean> foundNonEmpty = new Holder<Boolean>(Boolean.FALSE);
        // Go through all J-trees, check if any of them is non-empty
        traverseTrees(new Callback<JTree<K, V>>() {
            @Override
            public boolean callback(JTree<K, V> tree) {
                if (!tree.isEmpty(tx)) {
                    foundNonEmpty.setValue(Boolean.TRUE);
                    // False to stop search
                    return false;
                }
                // True to continue search
                return true;
            }
        }, tx);
        return !foundNonEmpty.getValue();
    }

    @Override
    protected void attachRoot(OMDPage<K, V, Coordinate<K>> rootPage, Transaction<MBR<K>, V> tx) {
        throw new UnsupportedOperationException("Not supported in this wrapper class");
    }

    @Override
    public void deleteRoot(OMDPage<K, V, Coordinate<K>> root, Owner owner) {
        throw new UnsupportedOperationException("Not supported in this wrapper class");
    }

    @Override
    protected void loadTree() {
        throw new UnsupportedOperationException("Not supported in this wrapper class");
    }

    @Override
    protected void updateInfoPage(Owner owner) {
        throw new UnsupportedOperationException("Not supported in this wrapper class");
    }

    @Override
    public String toString() {
        return getName() + ", root tree: " + rootTree;
    }

    @Override
    public void checkConsistency(Object... params) {
        rootTree.checkConsistency();
        if (!CHECK_ITEM_ORDERING)
            return;

        final K proto = keyPrototype.getMin().get(0);
        final Coordinate<K> cProto = new Coordinate<K>(keyPrototype.getDimensions(), proto);
        traverseTrees(new Callback<JTree<K, V>>() {
            @Override
            public boolean callback(JTree<K, V> tree) {
                traverseMDPages(mbrAllPred, OMDTree.getEntryOrderChecker(proto, valuePrototype,
                    cProto), internalOwner);
                return true;
            }
        }, internalOwner);
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
        rootTree.printDebugInfo();
    }

}
