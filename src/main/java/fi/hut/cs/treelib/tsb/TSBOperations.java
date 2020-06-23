package fi.hut.cs.treelib.tsb;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.mvbt.AbstractMVTOperations;
import fi.hut.cs.treelib.stats.Statistics.GlobalOperation;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.CollectionUtils;

public class TSBOperations<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMVTOperations<K, V, TSBPage<K, V>> {

    private static final Logger log = Logger.getLogger(TSBOperations.class);

    public enum SplitPolicy {
        IKS, WOB, Deferred
    };

    private TSBSplitPolicy<K, V> splitPolicy;
    private SplitPolicy splitPolicyType;

    protected TSBOperations(TSBTree<K, V> tree, SplitPolicy splitPolicy) {
        // this.splitPolicy = new IKSSplitPolicy<K, V>(tree);
        super(tree);
        setSplitPolicy(splitPolicy);
    }

    @Override
    protected TSBTree<K, V> getTree() {
        return (TSBTree<K, V>) tree;
    }

    protected void setSplitPolicy(SplitPolicy policy) {
        this.splitPolicyType = policy;
        if (policy.equals(SplitPolicy.IKS)) {
            this.splitPolicy = new IKSSplitPolicy<K, V>(getTree());
        } else if (policy.equals(SplitPolicy.WOB)) {
            this.splitPolicy = new WOBSplitPolicy<K, V>(getTree());
        } else {
            this.splitPolicy = new DeferredSplitPolicy<K, V>(getTree());
        }
    }

    protected SplitPolicy getSplitPolicy() {
        return splitPolicyType;
    }

    public V delete(PagePath<K, V, TSBPage<K, V>> path, K key, Transaction<K, V> tx) {
        TSBPage<K, V> page = path.getCurrent();
        assert page.isLeafPage();

        if (!page.contains(key, tx)) {
            // No entry to delete
            log.debug("No key " + key + " found on page " + page);
            return null;
        }

        if (!page.isFull()) {
            V value = page.delete(key, tx);
            // No underflow checking
            return value;
        }

        // No more space here, need to split
        if (log.isDebugEnabled())
            log.debug(String.format("Page %s is full when deleting, need to split", page
                .getName()));

        split(path, key, null, true, tx);

        // After split, delete from the new page
        return delete(path, key, tx);
    }

    @Override
    protected int countRequiredSpaceForSplit(PagePath<K, V, TSBPage<K, V>> path,
        Transaction<K, V> tx) {
        TSBPage<K, V> page = path.getCurrent();
        assert page != null;
        // Split policy defines how many new pages are needed
        return splitPolicy.countRequiredSpaceForSplit(page, tx);
    }

    /**
     * TSB split
     */
    @Override
    protected void splitSpaceEnsured(TSBPage<K, V> page, K key, PageID childID,
        PagePath<K, V, TSBPage<K, V>> path, Transaction<K, V> tx) {

        tree.getStatisticsLogger().log(Operation.OP_PAGE_SPLIT);
        tree.getStatisticsLogger().log(GlobalOperation.GO_PAGE_SPLIT);

        // Update the committed time stamps
        page.updateCommittedTimestamps();

        assert page == path.getCurrent();

        // Sets the siblings dirty, and sets the new key ranges for both of
        // the pages
        TSBPage<K, V>[] siblings = null;
        if (page.isLeafPage()) {
            siblings = splitPolicy.tsbSplitLeafEntries(page, path, tx);
        } else {
            siblings = splitPolicy.tsbSplitIndexEntries(page, path, tx);
        }
        assert page == path.getCurrent();

        path.ascend();
        // Find correct sibling page to descend to, release the rest of them

        page = null;
        boolean found = false;
        for (int i = 0; i < siblings.length; i++) {
            if (siblings[i].getKeyRange().contains(key, tx.getReadVersion())) {
                assert !found : key + "@" + tx.getReadVersion() + " at many: "
                    + CollectionUtils.toString(siblings);
                page = siblings[i];
                siblings[i] = null;
                found = true;
            }
        }
        assert found : String.format("None of the pages contains key %s@%d", key, tx
            .getReadVersion());

        // Descend to the correct page
        path.descend(page);
        // Unfix rest of the sibling pages
        for (TSBPage<K, V> sib : siblings) {
            // One of these is null, but unfix() can take a null argument
            buffer.unfix(sib, tx);
        }
    }

    /**
     * Override in TSB to use the page min version.
     */
    @Override
    protected MVKeyRange<K> getKeyRange(K min, K max, TSBPage<K, V> originatingPage,
        Transaction<K, V> tx) {
        return new MVKeyRange<K>(min, max, Math.min(tx.getReadVersion(), originatingPage
            .getKeyRange().getMinVersion()));
    }
}
