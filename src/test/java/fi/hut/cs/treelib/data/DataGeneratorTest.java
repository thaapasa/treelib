package fi.hut.cs.treelib.data;

import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.TreeLibTest;
import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.common.IntegerKey;

public class DataGeneratorTest extends TreeLibTest {

    public MBR<FloatKey> getMBR(float x1, float y1, float x2, float y2) {
        return new MBR<FloatKey>(new FloatKey(x1), new FloatKey(x2), new FloatKey(y1),
            new FloatKey(y2));
    }

    public void testCreateFloatMBRs() {
        MBR<FloatKey> bounds = getMBR(5, 5, 20, 20);
        MBR<FloatKey> minSize = getMBR(0, 0, 1, 1);
        MBR<FloatKey> maxSize = getMBR(0, 0, 3, 5);

        for (MBR<FloatKey> key : new DataGenerator<MBR<FloatKey>>(bounds, minSize, maxSize, 1000)) {
            assertGTE(key.getDistance(0).toFloat(), 1f);
            assertLTE(key.getDistance(0).toFloat(), 3f);
            assertGTE(key.getDistance(1).toFloat(), 1f);
            assertLTE(key.getDistance(1).toFloat(), 5f);

            assertTrue(bounds.contains(key));
            assertLTE(key.getArea().toFloat(), 15f);
        }
    }

    public void testCreateFloats() {
        int c = 0;
        for (FloatKey key : new DataGenerator<FloatKey>(new FloatKey(0), new FloatKey(10),
            new FloatKey(20), 1000)) {
            assertTrue(key + " < 10", key.toFloat() >= 10);
            assertTrue(key + " >= 20", key.toFloat() < 20);
            assertGTE(key.toFloat(), 10.0f);
            assertLT(key.toFloat(), 20.0f);
            c++;
        }
        assertEquals(1000, c);
    }

    public void testCreateIntegers() {
        int c = 0;
        for (IntegerKey key : new DataGenerator<IntegerKey>(new IntegerKey(0),
            new IntegerKey(10), new IntegerKey(20), 1000)) {
            assertGTE(key.intValue(), 10);
            assertLT(key.intValue(), 20);
            c++;
        }
        assertEquals(1000, c);
    }
}
