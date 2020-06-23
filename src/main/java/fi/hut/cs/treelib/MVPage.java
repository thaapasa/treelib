package fi.hut.cs.treelib;

import java.util.Collection;

import fi.hut.cs.treelib.common.PagePath;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

/**
 * Common interface for pages in a multiversion database tree.
 * 
 * @author tuska
 */
public interface MVPage<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> extends
    Page<K, V> {

    /**
     * Key-ranges are multi-version in MV pages.
     */
    @Override
    public MVKeyRange<K> getKeyRange();

    void insertRouter(P child, PagePath<K, V, P> path, Transaction<K, V> tx);

    PageValue<?> getEntry(K key, Transaction<K, V> tx);

    /**
     * For leaf pages: calls the callback for each entry that belongs to the
     * given version in this page.
     * 
     * @param version the version
     * @param range the range of entries to process; null to process all
     * entries
     * @param callback the callback
     * @return true if all entries were processed (all callbacks returned
     * true)
     */
    boolean processLeafEntries(int version, KeyRange<K> range, Callback<Pair<K, V>> callback);

    void collectPagesAtHeight(int height, Integer version,
        Collection<VisualizablePage<K, V>> result, Owner owner);

    public int getLiveEntryCount(int version);

    public int getLatestVersion();

}
