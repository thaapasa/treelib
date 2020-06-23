package fi.hut.cs.treelib.tsb;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.PagePath;
import fi.tuska.util.CollectionUtils;

public class IKSSplitPolicy<K extends Key<K>, V extends PageValue<?>> extends
    AbstractTSBSplitPolicy<K, V> {

    private static final Logger log = Logger.getLogger(IKSSplitPolicy.class);

    protected IKSSplitPolicy(TSBTree<K, V> tree) {
        super(tree);
    }

    /**
     * Used to split the entries of a leaf page between page and sibling. This
     * will also update the key ranges of both pages, and set both pages
     * dirty.
     */
    @SuppressWarnings("unchecked")
    public TSBPage<K, V>[] tsbSplitLeafEntries(TSBPage<K, V> page,
        PagePath<K, V, TSBPage<K, V>> path, Transaction<K, V> tx) {
        assert page.isLeafPage();

        // [T] Conclusions: If WORM storage is less than a factor of ten
        // cheaper than WMRM storage cost, then the IKS policy is a good
        // choice. ...
        // IKS: Isolated-Key-Split policy. The policy
        // 1. performs a time split only when not doing a key split, and uses
        // the time of last update as the splitting time.
        // 2. performs a key split whenever two thirds or more of the
        // splitting node consists of current data.
        // [/T]

        TSBPage<K, V> parent = path.getParent();
        MVKeyRange<K> oldPageRange = page.getKeyRange();

        // Create sibling
        TSBPage<K, V> sibling = tree.createSiblingPage(page, tx);

        // Whenever 2/3 or more of the splitting node consists of current data
        int aliveEntries = page.getAliveEntryCount(tx);
        log.debug("Splitting leaf page " + page + " with " + aliveEntries + " alive entries");
        if (aliveEntries >= page.getPageEntryCapacity() * 2 / 3) {
            // Perform a key split
            tsbKeySplitLeafPage(page, sibling, findKeySeparatorForLeafPage(page));
        } else {
            // Try to perform a time split using the time of last update as
            // the splitting time
            int ver = page.getLastUpdateVersion(tx);
            int entriesBeforeVer = page.getEntryMap().getEntriesBefore(ver);
            if (entriesBeforeVer < 2) {
                // Cannot split by time, need to do key split
                tsbKeySplitLeafPage(page, sibling, findKeySeparatorForLeafPage(page));
            } else {
                if (ver <= page.getKeyRange().getMinVersion()) {
                    assert ver > page.getKeyRange().getMinVersion() : ver + " <= "
                        + page.getKeyRange().getMinVersion();
                }
                tsbTimeSplit(page, sibling, ver);
            }
        }
        // Update router to page
        parent.updateChildRouter(page, oldPageRange);
        // Attach sibling to parent
        parent.attachChildPage(sibling);

        return CollectionUtils.array(page, sibling);
    }

    @Override
    public int countRequiredSpaceForSplit(TSBPage<K, V> page, Transaction<K, V> tx) {
        // Just one page needed for all splits
        return 1;
    }

}
