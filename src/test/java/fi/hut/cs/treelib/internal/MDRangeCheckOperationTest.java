package fi.hut.cs.treelib.internal;

import junit.framework.TestCase;
import fi.hut.cs.treelib.Coordinate;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.MDSMOPolicy;
import fi.hut.cs.treelib.mdtree.JTree;
import fi.hut.cs.treelib.mdtree.JTreeDatabase;
import fi.hut.cs.treelib.mdtree.OMDPage;
import fi.hut.cs.treelib.stats.NoStatistics;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;

public class MDRangeCheckOperationTest extends TestCase {

    private static final int PAGE_SIZE = 200;

    private PageStorage storage = new MemoryPageStorage(PAGE_SIZE);
    private JTree<FloatKey, IntegerValue> tree = new JTreeDatabase<FloatKey, IntegerValue>(32,
        new MDSMOPolicy(0.25, 0.375), new MBR<FloatKey>(2, new FloatKey(), true),
        IntegerValue.PROTOTYPE, storage).getDatabaseTree();

    private MBR<FloatKey> getMBR(float x1, float x2, float y1, float y2) {
        return new MBR<FloatKey>(new FloatKey(x1), new FloatKey(x2), new FloatKey(y1),
            new FloatKey(y2));
    }

    private OMDPage<FloatKey, IntegerValue, Coordinate<FloatKey>> getPage(float Xmin) {
        OMDPage<FloatKey, IntegerValue, Coordinate<FloatKey>> page = new OMDPage<FloatKey, IntegerValue, Coordinate<FloatKey>>(
            tree, new PageID(3), tree.getSearchKeyCreator());
        page.format(1);
        MBR<FloatKey> key = getMBR(Xmin, Xmin + 10, 10, 20);
        page.putContents(key, new IntegerValue(10));
        page.setSearchKey(key.getMin());
        page.setPageMBR(key);
        return page;
    }

    public void testSameXChecking() {
        MDRangeCheckOperation<FloatKey, IntegerValue> op = new MDRangeCheckOperation<FloatKey, IntegerValue>(
            3, NoStatistics.instance());

        op.callback(getPage(3));
        op.callback(getPage(6));
        op.callback(getPage(8));
        op.callback(getPage(10));
        op.callback(getPage(15));
        assertEquals(0, op.getSameXPageCount(1));
        assertEquals(0, op.getTotalSameMinPoints());
        assertEquals(0, op.getMaxSameMinPoints());

        // 2 x 18
        op.callback(getPage(18));
        assertEquals(0, op.getSameXPageCount(1));
        assertEquals(0, op.getTotalSameMinPoints());
        assertEquals(0, op.getMaxSameMinPoints());
        op.callback(getPage(18));
        assertEquals(2, op.getSameXPageCount(1));
        assertEquals(2, op.getTotalSameMinPoints());
        assertEquals(2, op.getMaxSameMinPoints());

        op.callback(getPage(20));
        op.callback(getPage(25));
        op.callback(getPage(30));

        // 4 x 34
        op.callback(getPage(34));
        assertEquals(2, op.getSameXPageCount(1));
        assertEquals(2, op.getTotalSameMinPoints());
        assertEquals(2, op.getMaxSameMinPoints());

        op.callback(getPage(34));
        assertEquals(4, op.getSameXPageCount(1));
        assertEquals(4, op.getTotalSameMinPoints());
        assertEquals(2, op.getMaxSameMinPoints());

        op.callback(getPage(34));
        assertEquals(5, op.getSameXPageCount(1));
        assertEquals(5, op.getTotalSameMinPoints());
        assertEquals(3, op.getMaxSameMinPoints());

        op.callback(getPage(34));
        assertEquals(6, op.getSameXPageCount(1));
        assertEquals(6, op.getTotalSameMinPoints());
        assertEquals(4, op.getMaxSameMinPoints());

        // 3 x 38
        op.callback(getPage(38));
        assertEquals(6, op.getSameXPageCount(1));
        assertEquals(6, op.getTotalSameMinPoints());
        assertEquals(4, op.getMaxSameMinPoints());

        op.callback(getPage(38));
        assertEquals(8, op.getSameXPageCount(1));
        assertEquals(8, op.getTotalSameMinPoints());
        assertEquals(4, op.getMaxSameMinPoints());

        op.callback(getPage(38));
        assertEquals(9, op.getSameXPageCount(1));
        assertEquals(9, op.getTotalSameMinPoints());
        assertEquals(4, op.getMaxSameMinPoints());
    }
}
