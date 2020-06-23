package fi.hut.cs.treelib.internal;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.tuska.util.Callback;

public class CountVisitedPagesOperation<K extends Key<K>, V extends PageValue<?>> implements
    Callback<Page<K, V>> {

    private int count = 0;

    @Override
    public boolean callback(Page<K, V> page) {
        count++;
        return true;
    }

    public int getPageCount() {
        return count;
    }

}
