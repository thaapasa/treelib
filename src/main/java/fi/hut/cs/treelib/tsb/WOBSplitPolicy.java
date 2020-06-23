package fi.hut.cs.treelib.tsb;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.PagePath;
import fi.tuska.util.CollectionUtils;

public class WOBSplitPolicy<K extends Key<K>, V extends PageValue<?>> extends
    AbstractTSBSplitPolicy<K, V> {

    private static final Logger log = Logger.getLogger(WOBSplitPolicy.class);

    private final double leafSplitThreshold = 0.67;

    protected WOBSplitPolicy(TSBTree<K, V> tree) {
        super(tree);
    }

    @Override
    public int countRequiredSpaceForSplit(TSBPage<K, V> page, Transaction<K, V> tx) {
        if (page.isLeafPage()) {
            // Leaf pages (data pages)
            double svcu = page.getSingleVersionCurrentUtilization(tx);
            if (svcu > leafSplitThreshold) {
                // ... do a key split after a time split
                return 2;
            } else {
                return 1;
            }
        } else {
            // Index pages
            return 1;
        }
    }

    /**
     * Used to split the entries of a leaf page between page and sibling. This
     * will also update the key ranges of both pages, and set both pages
     * dirty.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TSBPage<K, V>[] tsbSplitLeafEntries(TSBPage<K, V> page,
        PagePath<K, V, TSBPage<K, V>> path, Transaction<K, V> tx) {
        assert page.isLeafPage();

        // TSB-tree does both key splits, which are similar to key splits in
        // the B-tree, and time splits.

        // One point to stress is that when a page is time split, we create a
        // new page for the historical data. The original page remains the
        // current page.

        // Whenever a data page fills up, it is split.
        // We need to decide whether the split is a time split, a key split,
        // or both. Immortal DB never does an isolated key split.
        // In Immortal DB, if a key split is needed, we always perform a time
        // split before it, which we call the WOB-tree split policy.

        // We define single version current utilization for a page (SVCUpage)
        // as the size of the page's current data divided by the page size
        // (both in bytes). We specify a threshold value Thresh for this
        // utilization to control page splits. If, when a page fills
        // completely, SVCUpage > Thresh then we do a key split after a time
        // split. Otherwise, we perform only a time split.

        TSBPage<K, V> parent = path.getParent();
        MVKeyRange<K> oldPageRange = page.getKeyRange();

        final int splitTime = tx.getReadVersion();
        final double svcu = page.getSingleVersionCurrentUtilization(tx);
        if (svcu > leafSplitThreshold
            || page.getKeyRange().getMinVersion() == tx.getReadVersion()) {
            // ... do a key split after a time split
            assert parent.getFreeEntries() >= 2 : "Not enough space in parent " + parent;

            log.debug(String.format("SVCU %.2f larger than threshold %.2f, "
                + "performing timesplit (time: %d) + keysplit", svcu, leafSplitThreshold,
                splitTime));

            // Create sibling for historical page
            TSBPage<K, V> hSibling = null;
            if (page.getMinEntryVersion() < splitTime) {
                // Only do the time-split if there is actual historical
                // information on the page; otherwise skip the time-split...
                hSibling = tree.createSiblingPage(page, tx);
                tsbTimeSplit(page, hSibling, splitTime);

                // Update router to page
                parent.updateChildRouter(page, oldPageRange);
                // Attach sibling to parent
                parent.attachChildPage(hSibling);
                // Update old key range
                oldPageRange = page.getKeyRange();
            }
            // assert page.getSingleVersionCurrentUtilization(tx) >
            // leafSplitThreshold;

            // Create sibling for key-splitting the page
            TSBPage<K, V> kSibling = tree.createSiblingPage(page, tx);
            tsbKeySplitLeafPage(page, kSibling, findKeySeparatorForLeafPage(page));

            // Update router to page
            parent.updateChildRouter(page, oldPageRange);
            // Attach sibling to parent
            parent.attachChildPage(kSibling);

            return hSibling != null ? CollectionUtils.array(page, kSibling, hSibling)
                : CollectionUtils.array(page, kSibling);
        } else {
            // ... perform only a time split.
            log.debug(String.format(
                "SVCU %.2f less than threshold %.2f, performing only timesplit", svcu,
                leafSplitThreshold));

            if (page.getMinEntryVersion() < splitTime) {
                // Do a time split
                return timeSplitLeaf(page, parent, splitTime, tx);
            } else {
                // Can't time-split, must key-split
                return keySplitLeaf(page, parent, tx);
            }
        }
    }

}
