package fi.hut.cs.treelib.common;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;

public class DummySMOPolicy implements SMOPolicy {

    @Override
    public int getMaxEntries(Page<?, ?> page) {
        return page.getPageEntryCapacity();
    }

    @Override
    public int getMaxEntriesAfterSMO(Page<?, ?> page) {
        return page.getPageEntryCapacity();
    }

    @Override
    public int getMinEntries(Page<?, ?> page) {
        return 0;
    }

    @Override
    public int getMinEntriesAfterSMO(Page<?, ?> page) {
        return 0;
    }

    @Override
    public double getMinFillRate() {
        return 0;
    }

    @Override
    public boolean isAboutToOverflow(Page<?, ?> page) {
        return page.isFull();
    }

    @Override
    public <K extends Key<K>, V extends PageValue<?>> boolean isAboutToUnderflow(Page<K, V> page,
        PagePath<K, V, ? extends Page<K, V>> path) {
        return page.getEntryCount() == 0;
    }

}
