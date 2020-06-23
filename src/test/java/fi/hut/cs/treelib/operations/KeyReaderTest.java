package fi.hut.cs.treelib.operations;

import java.io.StringReader;

import junit.framework.TestCase;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.data.KeyReader;
import fi.tuska.util.iterator.ReaderIterator;

public class KeyReaderTest extends TestCase {

    private static final FloatKey FLOAT = new FloatKey();

    private static final MBR<FloatKey> MBR = new MBR<FloatKey>(2, FLOAT, true);

    public void testKeyRead() {
        String src = "1\n" + "2\n" + "5.553\n" + "\n" + "aiksdjs\n" + "    \n" + "6\n";
        KeyReader<FloatKey> reader = new KeyReader<FloatKey>(new ReaderIterator(new StringReader(
            src)), FLOAT);

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(1f), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(2f), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(5.553f), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(6f), reader.next());

        assertFalse(reader.hasNext());
    }

    public void testKeySkipRead() {
        String src = "1\n" + "2\n" + "5.553\n" + "\n" + "aiksdjs\n" + "    \n" + "6\n";
        KeyReader<FloatKey> reader = new KeyReader<FloatKey>(new ReaderIterator(new StringReader(
            src)), FLOAT, null, 2L);

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(1f), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(6f), reader.next());

        assertFalse(reader.hasNext());
    }

    public void testSkipPartsKeyRead() {
        String src = "23 1\n" + "ks   2\n" + "ad 5.553\n" + "\n" + "aiksdjs\n" + "    \n"
            + "90   6\n";
        KeyReader<FloatKey> reader = new KeyReader<FloatKey>(new ReaderIterator(new StringReader(
            src)), FLOAT);

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(1f), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(2f), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(5.553f), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(6f), reader.next());

        assertFalse(reader.hasNext());
    }

    private MBR<FloatKey> getMBR(float x1, float x2, float y1, float y2) {
        return new MBR<FloatKey>(new FloatKey(x1), new FloatKey(x2), new FloatKey(y1),
            new FloatKey(y2));
    }

    public void testLimitKeyReading() {
        String src = "1\n" + "2\n" + "5.553\n" + "\n" + "aiksdjs\n" + "    \n" + "6\n";
        KeyReader<FloatKey> reader = new KeyReader<FloatKey>(new ReaderIterator(new StringReader(
            src)), FLOAT, 2L);

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(1f), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(2f), reader.next());

        assertFalse(reader.hasNext());

        reader = new KeyReader<FloatKey>(new ReaderIterator(new StringReader(src)), FLOAT, 0L);
        assertFalse(reader.hasNext());

        reader = new KeyReader<FloatKey>(new ReaderIterator(new StringReader(src)), FLOAT, 1L);
        assertTrue(reader.hasNext());
        assertEquals(new FloatKey(1f), reader.next());
        assertFalse(reader.hasNext());
    }

    public void testMBRKeyRead() {
        String src = "1 2 5 6\n" + "2\n" + "8 3.3 9 2.3\n" + "\n" + "3 4 6 10 12 15\n"
            + "\n...ads\n";
        KeyReader<MBR<FloatKey>> reader = new KeyReader<MBR<FloatKey>>(new ReaderIterator(
            new StringReader(src)), MBR);

        assertTrue(reader.hasNext());
        assertEquals(getMBR(1, 2, 5, 6), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(getMBR(3.3f, 8f, 2.3f, 9f), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(getMBR(3, 4, 6, 10), reader.next());

        assertFalse(reader.hasNext());
    }

    public void testMBRKeyPartsRead() {
        String src = "7 8 1 2 5 6\n" + "2\n" + "8 3.3 9 2.3\n" + "\n" + "3 4 6 10 12 15\n"
            + "\n...ads\n";
        KeyReader<MBR<FloatKey>> reader = new KeyReader<MBR<FloatKey>>(new ReaderIterator(
            new StringReader(src)), MBR);

        assertTrue(reader.hasNext());
        assertEquals(getMBR(1, 2, 5, 6), reader.next());

        assertTrue(reader.hasNext());
        assertEquals(getMBR(6, 10, 12, 15), reader.next());

        assertFalse(reader.hasNext());
    }

}
