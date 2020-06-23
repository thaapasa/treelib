package fi.hut.cs.treelib.internal;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVPage;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class CountAliveEntriesOperation<K extends Key<K>, V extends PageValue<?>> implements
    Callback<Page<K, V>> {

    private static final Logger log = Logger.getLogger(CountAliveEntriesOperation.class);

    private int count;
    private int version;

    private Set<PageID> visited = new HashSet<PageID>();
    private Set<K> visitedKeys = new HashSet<K>();

    public CountAliveEntriesOperation(int version) {
        this.version = version;
        this.count = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean callback(Page<K, V> page) {
        if (!(page instanceof MVPage)) {
            log.warn("CountAliveEntriesOperation not supported for non-multiversion pages");
            return false;
        }

        PageID pageID = page.getPageID();

        // Do not re-visit pages that have already been visited!
        if (visited.contains(pageID))
            return true;
        visited.add(pageID);

        MVPage<K, V, ?> mvPage = (MVPage<K, V, ?>) page;
        MVKeyRange<K> pageRange = mvPage.getKeyRange();
        // Skip non-alive pages
        if (!pageRange.containsVersion(version))
            return true;

        mvPage.processLeafEntries(version, null, new Callback<Pair<K, V>>() {
            @Override
            public boolean callback(Pair<K, V> entry) {
                K key = entry.getFirst();
                if (visitedKeys.contains(key))
                    return true;
                visitedKeys.add(key);
                count++;
                return true;
            }
        });

        return true;
    }

    public int getEntryCount() {
        return count;
    }

}
