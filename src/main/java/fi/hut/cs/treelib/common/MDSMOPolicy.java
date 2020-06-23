package fi.hut.cs.treelib.common;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVPage;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;

public class MDSMOPolicy implements SMOPolicy {

    private final double mergeAtRate;
    private final double toleranceRate;

    public MDSMOPolicy(double mergeAtRate, double toleranceRate) {
        this.mergeAtRate = mergeAtRate;
        this.toleranceRate = toleranceRate;
    }

    @Override
    public int getMaxEntries(Page<?, ?> page) {
        return Math.round(page.getPageEntryCapacity());
    }

    @Override
    public int getMaxEntriesAfterSMO(Page<?, ?> page) {
        return page.getPageEntryCapacity();
    }

    @Override
    public int getMinEntries(Page<?, ?> page) {
        return (int) Math.round(page.getPageEntryCapacity() * mergeAtRate);
    }

    @Override
    public int getMinEntriesAfterSMO(Page<?, ?> page) {
        return (int) Math.round(page.getPageEntryCapacity() * toleranceRate);
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
        return mergeAtRate;
    }

}
