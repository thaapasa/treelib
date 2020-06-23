package fi.hut.cs.treelib.data;

import java.util.Iterator;
import java.util.regex.Pattern;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;

/**
 * Assumption: input data consists of a parsable representation of key of type
 * K. The data may contain other information before the key itself. It is
 * assumed that the data is in a tab/space separated format, with a number key
 * taking the last separated position; and an MBR taking the last 2 * dim(MBR)
 * positions (2-dimensional MBR will take 4 positions, for example).
 * 
 * All lines must contain the same amount of separated parts, otherwise
 * parsing will fail (will read a wrong part of the line).
 * 
 * Example of MBR format, here the "1 2 3 4" is the key part:
 * 
 * <pre>
 * My-data    32    1  2  3  4
 * </pre>
 * 
 * @author tuska
 * 
 * @param <K>
 */
public class KeyReader<K extends Key<K>> implements Iterable<K>, Iterator<K> {

    private static final String SPLIT_PAT = "[ \t]+";
    private static final Pattern SPLITTER = Pattern.compile(SPLIT_PAT);

    private final K keyPrototype;
    private final Long limit;
    private final long skip;

    private Iterator<String> stringSource;
    private K nextKey;
    private boolean eof;
    private int skipParts;
    private long itemsReturned = 0;

    public KeyReader(Iterator<String> src, K keyPrototype) {
        this(src, keyPrototype, null, null);
    }

    public KeyReader(Iterator<String> src, K keyPrototype, Long limit) {
        this(src, keyPrototype, limit, null);
    }

    public KeyReader(Iterator<String> src, K keyPrototype, Long limit, Long skip) {
        this.stringSource = src;
        this.keyPrototype = keyPrototype;
        this.limit = limit;
        this.skip = (skip != null && skip >= 0) ? skip.longValue() : 0;
        eof = false;
        skipParts = -1;
        readNext();
    }

    @SuppressWarnings("unchecked")
    private void initializeSkipParts(String line) {
        String[] tempLine = SPLITTER.split(line);
        if (keyPrototype instanceof MBR) {
            MBR<K> mbrProto = (MBR<K>) keyPrototype;
            // MBR's have two times the number of dimensions parts
            skipParts = tempLine.length - mbrProto.getDimensions() * 2;
        } else {
            // Normal numbers have one part
            skipParts = tempLine.length - 1;
        }
        if (skipParts < 0) {
            throw new RuntimeException("Invalid number of key parts: " + tempLine.length);
        }
        // At this point, skipParts >= 0 always
    }

    private K readNextKey() {
        while (true) {
            if (!stringSource.hasNext())
                return null;

            String line = stringSource.next();
            if (line != null) {
                line = line.trim();
                if (line.equals(""))
                    continue;
                // On first line, check how many parts there are
                if (skipParts < 0) {
                    initializeSkipParts(line);
                }
                // Try to parse the line
                // First, skip the initial parts before the key
                if (skipParts > 0) {
                    // Skip parts before the key
                    String[] p = SPLITTER.split(line, skipParts + 1);
                    if (p.length < skipParts + 1)
                        continue;
                    line = p[skipParts];
                }
                K key = keyPrototype.parse(line);
                if (key != null) {
                    // Next key found, mark it and return
                    return key;
                }
            }
        }
    }

    private void readNext() {
        nextKey = null;
        if (!stringSource.hasNext())
            return;
        nextKey = readNextKey();

        // If skip > 0, discard some keys
        for (long i = 0; i < skip && !eof; i++) {
            readNextKey();
        }
    }

    @Override
    public Iterator<K> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return nextKey != null && (limit == null || itemsReturned < limit.intValue());
    }

    @Override
    public K next() {
        K result = nextKey;
        readNext();
        itemsReturned++;
        return result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove() not supported");
    }

}
