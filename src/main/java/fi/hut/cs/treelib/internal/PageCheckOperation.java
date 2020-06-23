package fi.hut.cs.treelib.internal;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Value;
import fi.tuska.util.Callback;

public class PageCheckOperation<K extends Key<K>, V extends PageValue<?>> implements
    Callback<Page<K, V>> {

    private final StatisticsLogger stats;

    public PageCheckOperation(StatisticsLogger stats) {
        this.stats = stats;
    }

    @Override
    public boolean callback(Page<K, V> page) {
        stats.log(Value.VAL_PAGE_FILL_RATIO, page.getFillRatio());
        if (page.isLeafPage())
            stats.log(Value.VAL_LEAF_PAGE_FILL_RATIO, page.getFillRatio());
        else
            stats.log(Value.VAL_INDEX_PAGE_FILL_RATIO, page.getFillRatio());

        // Continue
        return true;
    }

    public String getSummary() {
        return "";
    }

}
