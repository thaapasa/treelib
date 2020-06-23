package fi.hut.cs.treelib.tsb;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.PagePath;

public interface TSBSplitPolicy<K extends Key<K>, V extends PageValue<?>> {

    /**
     * @return an array containing the page, the new sibling page and all
     * other sibling pages created by the split operation. All pages will
     * contain one fix (page contains the original fix, new pages have a
     * single fix).
     */
    TSBPage<K, V>[] tsbSplitLeafEntries(TSBPage<K, V> page, PagePath<K, V, TSBPage<K, V>> path,
        Transaction<K, V> tx);

    /**
     * @return an array containing the page, the new sibling page and all
     * other sibling pages created by the split operation. All pages will
     * contain one fix (page contains the original fix, new pages have a
     * single fix).
     */
    TSBPage<K, V>[] tsbSplitIndexEntries(TSBPage<K, V> page, PagePath<K, V, TSBPage<K, V>> path,
        Transaction<K, V> tx);

    int countRequiredSpaceForSplit(TSBPage<K, V> page, Transaction<K, V> tx);

}
