package fi.hut.cs.treelib.common;

import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.TreeLibTest;
import fi.hut.cs.treelib.storage.TestPage;

public class NonThrashingSMOPolicyTest extends TreeLibTest {

    public void testValues() {
        SMOPolicy p = new NonThrashingSMOPolicy(0.2, 0.2);
        for (int i = 5; i < 1000; i++) {
            testValues(i, p);
        }
    }

    public void testValues(int capacity, SMOPolicy p) {
        log.debug("Testing page size " + capacity);
        TestPage page = new TestPage(new PageID(1), capacity, 1);
        int min = p.getMinEntries(page);
        int smin = p.getMinEntriesAfterSMO(page);
        int smax = p.getMaxEntriesAfterSMO(page);
        assertGTE(smax, 2 * smin);
        assertGTE(2 * min, smin);
        log.debug(String.format("Values are %d, %d-%d", min, smin, smax));
    }
}
