package fi.hut.cs.treelib.internal;

import java.util.HashSet;
import java.util.Set;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Tree;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class CountEntriesOperation<K extends Key<K>, V extends PageValue<?>> implements
    Callback<Page<K, V>> {

    private int count = 0;

    private Set<PageID> visitedPages = new HashSet<PageID>();

    private final boolean possibleDuplicatedKeys;

    public CountEntriesOperation(Database<K, V, ?> db) {
        this.possibleDuplicatedKeys = db.isMultiVersion();
    }

    public CountEntriesOperation(Tree<K, V, ?> tree) {
        this.possibleDuplicatedKeys = tree.isMultiVersion();
    }

    @Override
    public boolean callback(Page<K, V> page) {
        // Skip index pages
        if (!page.isLeafPage())
            return true;

        PageID pageID = page.getPageID();

        if (possibleDuplicatedKeys) {
            // Do not re-visit pages that have already been visited!
            if (visitedPages.contains(pageID))
                return true;
            visitedPages.add(pageID);
        }

        assert page.isLeafPage();
        page.processEntries(new Callback<Pair<KeyRange<K>, PageValue<?>>>() {
            @Override
            public boolean callback(Pair<KeyRange<K>, PageValue<?>> entry) {
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
