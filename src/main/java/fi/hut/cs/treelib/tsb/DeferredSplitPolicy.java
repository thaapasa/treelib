package fi.hut.cs.treelib.tsb;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.PagePath;

public class DeferredSplitPolicy<K extends Key<K>, V extends PageValue<?>> extends
    AbstractTSBSplitPolicy<K, V> {

    private static final Logger log = Logger.getLogger(DeferredSplitPolicy.class);

    private final double leafSplitThreshold = 0.67;

    protected DeferredSplitPolicy(TSBTree<K, V> tree) {
        super(tree);
    }

    @Override
    public int countRequiredSpaceForSplit(TSBPage<K, V> page, Transaction<K, V> tx) {
        return 1;
    }

    /**
     * Used to split the entries of a leaf page between page and sibling. This
     * will also update the key ranges of both pages, and set both pages
     * dirty.
     */
    @Override
    public TSBPage<K, V>[] tsbSplitLeafEntries(TSBPage<K, V> page,
        PagePath<K, V, TSBPage<K, V>> path, Transaction<K, V> tx) {
        assert page.isLeafPage();

        // TSB-tree does both key splits, which are similar to key splits in
        // the B-tree, and time splits.

        // One point to stress is that when a page is time split, we create a
        // new page for the historical data. The original page remains the
        // current page.

        // When the utilization of the current version in a full current page
        // (called single version current utilization or SVCU) is less than
        // Th, the page is time split as before.
        // When SVCU >= Th, then instead of doing a time split followed by a
        // key split, we only do a time split. But, we remember that we have
        // exceeded Th by marking the page. When the page fills again, we then
        // do a key split without doing an immediately preceding time split.
        // Rather, the earlier time split substitutes for this.

        TSBPage<K, V> parent = path.getParent();

        if (page.isDeferredSplit()) {
            // We should complete the deferred key-split
            return keySplitLeaf(page, parent, tx);
        }

        final int splitTime = tx.getReadVersion();
        final double svcu = page.getSingleVersionCurrentUtilization(tx);
        if (svcu >= leafSplitThreshold
            || page.getKeyRange().getMinVersion() == tx.getReadVersion()) {
            // ... do a time split and defer a key split
            assert parent.getFreeEntries() >= 1 : "Not enough space in parent " + parent;

            log.debug(String.format("SVCU %.2f larger than threshold %.2f, "
                + "performing timesplit (time: %d) + deferred keysplit", svcu,
                leafSplitThreshold, splitTime));

            if (page.getMinEntryVersion() < splitTime) {
                // Do a time split + a deferred key split
                TSBPage<K, V>[] pages = timeSplitLeaf(page, parent, splitTime, tx);

                // Now we should do a key-split, but let's not, as this is the
                // deferred split policy

                // In TSB-tree, page remains the current page, and hSibling
                // (new page) receives the historical entries
                page.setDeferredSplit(true);
                return pages;
            } else {
                // Can't time-split, must key-split
                return keySplitLeaf(page, parent, tx);
            }
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
