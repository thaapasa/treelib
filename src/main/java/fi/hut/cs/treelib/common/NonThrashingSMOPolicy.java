package fi.hut.cs.treelib.common;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVPage;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;

public class NonThrashingSMOPolicy implements SMOPolicy {

    private final double minFillRate;
    private final double tolerance;

    public NonThrashingSMOPolicy(double minFillRate, double tolerance) {
        assert minFillRate < 1.0d;
        assert 2 * minFillRate <= 1.0d;
        assert minFillRate + tolerance <= 1 - tolerance;
        assert tolerance <= minFillRate;

        this.minFillRate = minFillRate;
        this.tolerance = tolerance;
    }

    @Override
    public int getMaxEntries(Page<?, ?> page) {
        return page.getPageEntryCapacity();
    }

    @Override
    public int getMaxEntriesAfterSMO(Page<?, ?> page) {
        int max = (int) Math.floor(page.getPageEntryCapacity() * (1.0d - tolerance));
        int min = getMinEntriesAfterSMO(page);
        int ret = Math.max(max, 2 * min);
        assert ret <= page.getPageEntryCapacity();
        return ret;
    }

    @Override
    public int getMinEntries(Page<?, ?> page) {
        int min = (int) Math.floor(page.getPageEntryCapacity() * minFillRate);
        int smin = getMinEntriesAfterSMO(page);
        if (2 * min < smin) {
            min = (int) Math.ceil(smin / 2.0);
        }
        assert 2 * min >= smin;
        return min;
    }

    @Override
    public int getMinEntriesAfterSMO(Page<?, ?> page) {
        return (int) Math.floor(page.getPageEntryCapacity() * (minFillRate + tolerance));
    }

    @Override
    public boolean isAboutToOverflow(Page<?, ?> page) {
        return page.isFull();
    }

    @Override
    public <K extends Key<K>, V extends PageValue<?>> boolean isAboutToUnderflow(Page<K, V> page,
        PagePath<K, V, ? extends Page<K, V>> path) {
        int min = getMinEntries(page);
        int entries = 0;
        if (page instanceof MVPage<?, ?, ?>) {
            MVPage<?, ?, ?> mvPage = (MVPage<?, ?, ?>) page;
            entries = mvPage.getLiveEntryCount(mvPage.getLatestVersion());
        } else {
            entries = page.getEntryCount();
        }
        // Check if the page is a root page
        if (page.isRoot(path)) {
            return path.size() == 1 ? entries == 1 : entries == 2;
        }

        assert entries >= min : "Page entries already underflown";
        return entries == min;
    }

    @Override
    public double getMinFillRate() {
        return minFillRate;
    }

}
