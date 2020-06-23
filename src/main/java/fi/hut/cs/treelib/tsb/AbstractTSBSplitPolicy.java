package fi.hut.cs.treelib.tsb;

import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.UpdateMarker;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Quad;

public abstract class AbstractTSBSplitPolicy<K extends Key<K>, V extends PageValue<?>> implements
    TSBSplitPolicy<K, V> {

    private static final Logger log = Logger.getLogger(AbstractTSBSplitPolicy.class);

    protected final TSBTree<K, V> tree;

    protected AbstractTSBSplitPolicy(TSBTree<K, V> tree) {
        this.tree = tree;
    }

    /**
     * Key-split the entries from page to sibling; separator is used as the
     * separator key.
     * 
     * @param separator all entries with keys >= separator go to sibling; rest
     * remain in page.
     */
    protected void tsbKeySplitLeafPage(TSBPage<K, V> page, TSBPage<K, V> sibling, K separator) {
        assert separator != null;
        assert page.isLeafPage();
        assert sibling.isLeafPage();
        log.debug("Performing key split on leaf page " + page + " based on separator key "
            + separator);
        for (Iterator<Quad<K, Integer, Boolean, UpdateMarker<V>>> it = page.getLeafContents()
            .iterator(); it.hasNext();) {
            Quad<K, Integer, Boolean, UpdateMarker<V>> entry = it.next();

            K key = entry.getFirst();
            if (key.compareTo(separator) >= 0) {
                // r >= separator; move entry to sibling
                int ver = entry.getSecond();
                boolean temp = entry.getThird();
                UpdateMarker<V> value = entry.getFourth();
                it.remove();
                sibling.putLeafEntry(key, ver, temp, value);
            }
        }
        MVKeyRange<K> org = page.getKeyRange();
        // New key ranges: min -> separator for page
        page.setKeyRange(new MVKeyRange<K>(org.getMin(), separator, org.getVersionRange()));
        // ... and separator -> max for sibling
        sibling.setKeyRange(new MVKeyRange<K>(separator, org.getMax(), org.getVersionRange()));
        // Both pages have now been set dirty
        log.debug("After split: " + page + " and " + sibling);
    }

    /**
     * Time-split the entries from page to sibling; splitTime is used to
     * separate the entries. This method introduces some redundancy as entries
     * are duplicated in many pages.
     * 
     * @param splitTime all entries with timeRange >= splitTime (incl. current
     * entries) remain in page; rest and those equal to splitTime (historical
     * entries) will go to sibling.
     */
    protected void tsbTimeSplit(TSBPage<K, V> page, TSBPage<K, V> sibling, int splitTime) {
        log.debug("Performing time split on page " + page + " based on split time " + splitTime);
        MVKeyRange<K> org = page.getKeyRange();
        assert splitTime > org.getMinVersion() : splitTime + " <= " + org.getMinVersion();
        assert splitTime < org.getMaxVersion() : splitTime + " < " + org.getMaxVersion();
        if (page.isLeafPage()) {
            page.splitLeafEntriesTo(sibling, splitTime);
        } else {
            for (Iterator<Entry<MVKeyRange<K>, PageValue<?>>> it = page.getContents().entrySet()
                .iterator(); it.hasNext();) {
                Entry<MVKeyRange<K>, PageValue<?>> entry = it.next();

                MVKeyRange<K> r = entry.getKey();
                if (r.getMinVersion() < splitTime) {
                    PageValue<?> value = entry.getValue();
                    // r has versions < splitTime; this goes to sibling
                    sibling.putContents(r, value);

                    // If r is entirely historical (does not cross splitTime),
                    // delete it
                    if (r.getMaxVersion() <= splitTime) {
                        // r is historical, no need to leave it to page
                        it.remove();
                        assert !r.containsVersion(splitTime);
                    }
                } else {
                    // Entry belongs to page (only)
                }
            }
        }

        // New version ranges: min -> splitTime for sibling
        sibling.setKeyRange(new MVKeyRange<K>(org, org.getMinVersion(), splitTime));
        // ... and splitTime -> max for page
        // org.maxVersion should be infinite
        assert org.getMaxVersion().intValue() == Integer.MAX_VALUE : org.getMaxVersion() + ": "
            + page;
        page.setKeyRange(new MVKeyRange<K>(org, splitTime, org.getMaxVersion()));
        // Both pages have now been set dirty
        log.debug("After split: " + page + " and " + sibling);
    }

    protected K findKeySeparatorForLeafPage(TSBPage<K, V> page) {
        assert page.isLeafPage();
        // For IKS split policy:
        // Could not find from [T] info on how the key should be selected.
        // This is one guess.
        // For Immortal DB split policy (WOB-tree split policy):
        // Key split is performed only after a time split, so the mid entry is
        // a logical separator (separates entries evenly, and current version
        // utilization will be equal in both pages resulting from the split)
        return page.getMidEntryKey();
    }

    /**
     * Used to split the entries in an index page between page and sibling.
     * This will also update the key ranges of both pages, and set both pages
     * dirty.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TSBPage<K, V>[] tsbSplitIndexEntries(TSBPage<K, V> page,
        PagePath<K, V, TSBPage<K, V>> path, Transaction<K, V> tx) {
        assert !page.isLeafPage();
        // [T] Find a split time at which historical index terms can migrate
        // to an historical node without any current index terms ending up
        // there as well. This involves finding the oldest current index term
        // and using its time as the split time. This approach was adopted for
        // simulation, though not without having to deal with several subtle
        // bugs in the splitting process. [/T]

        TSBPage<K, V> parent = path.getParent();
        assert parent.getFreeEntries() >= 1;

        MVKeyRange<K> oldPageRange = page.getKeyRange();

        // Create sibling
        TSBPage<K, V> sibling = tree.createSiblingPage(page, tx);

        int splitTime = page.getOldestCurrentUpdateVersion(tx.getReadVersion());
        if (splitTime > page.getKeyRange().getMinVersion()) {
            // Default: time split
            tsbTimeSplit(page, sibling, splitTime);
        } else {
            // Page has at least one current entry that has not been
            // time-split (lasts through the entire version range of this
            // page).
            // Thus, this page can be key-split next to that entry.
            K separator = page.selectClosestUnsplitKey(page.getEntryCount() / 2, tx);
            tsbKeySplitIndexPage(page, sibling, separator);
        }

        // Update router to page
        parent.updateChildRouter(page, oldPageRange);
        // Attach sibling to parent
        parent.attachChildPage(sibling);
        return CollectionUtils.array(page, sibling);

        // In some cases, the split frees only a single slot, and the
        // operation that caused the split may need two free slots.
        // Thus, the split operation that calls this method must check if
        // there is enough space and possibly re-split the page.
    }

    /**
     * Key-split the entries from page to sibling; separator is used as the
     * separator key.
     * 
     * @param separator all entries with keys >= separator go to sibling; rest
     * remain in page.
     */
    protected void tsbKeySplitIndexPage(TSBPage<K, V> page, TSBPage<K, V> sibling, K separator) {
        assert separator != null;
        assert !page.isLeafPage();
        assert !sibling.isLeafPage();
        log.debug("Performing key split on index page " + page + " based on separator key "
            + separator);
        for (Iterator<Entry<MVKeyRange<K>, PageValue<?>>> it = page.getContents().entrySet()
            .iterator(); it.hasNext();) {
            Entry<MVKeyRange<K>, PageValue<?>> entry = it.next();

            MVKeyRange<K> r = entry.getKey();
            if (r.getMin().compareTo(separator) >= 0) {
                // r >= separator; move entry to sibling
                PageValue<?> val = entry.getValue();
                it.remove();
                sibling.putContents(r, val);
            } else {
                // Check that this is a proper key split (separator does not
                // break through some entry's key-range).
                assert !r.contains(separator) : r + " contains " + separator
                    + "; splitting page " + page;
            }
        }
        MVKeyRange<K> org = page.getKeyRange();
        // New key ranges: min -> separator for page
        page.setKeyRange(new MVKeyRange<K>(org.getMin(), separator, org.getVersionRange()));
        // ... and separator -> max for sibling
        sibling.setKeyRange(new MVKeyRange<K>(separator, org.getMax(), org.getVersionRange()));
        // Both pages have now been set dirty
        log.debug("After split: " + page + " and " + sibling);
    }

    @SuppressWarnings("unchecked")
    protected TSBPage<K, V>[] timeSplitLeaf(TSBPage<K, V> page, TSBPage<K, V> parent,
        int splitTime, Transaction<K, V> tx) {
        MVKeyRange<K> oldPageRange = page.getKeyRange();

        // Create sibling for historical page
        TSBPage<K, V> hSibling = null;
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

        return CollectionUtils.array(page, hSibling);
    }

    @SuppressWarnings("unchecked")
    protected TSBPage<K, V>[] keySplitLeaf(TSBPage<K, V> page, TSBPage<K, V> parent,
        Transaction<K, V> tx) {
        MVKeyRange<K> oldPageRange = page.getKeyRange();

        // Create sibling for key-splitting the page
        TSBPage<K, V> kSibling = tree.createSiblingPage(page, tx);
        tsbKeySplitLeafPage(page, kSibling, findKeySeparatorForLeafPage(page));

        // Update router to page
        parent.updateChildRouter(page, oldPageRange);
        // Attach sibling to parent
        parent.attachChildPage(kSibling);

        // Clear deferred split values
        page.setDeferredSplit(false);
        kSibling.setDeferredSplit(false);

        return CollectionUtils.array(page, kSibling);
    }

}
