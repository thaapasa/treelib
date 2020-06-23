package fi.hut.cs.treelib.btree;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.TreeLibTest;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.NonThrashingSMOPolicy;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.util.ByteUtils;

public class BTreePageTest extends TreeLibTest {

    private static final int PAGE_SIZE = 1000;
    private byte[] dataBytes;
    private ByteBuffer data;
    private BTree<IntegerKey, StringValue> tree;
    private BTreeOperations<IntegerKey, StringValue> ops;
    private final StringValue valueProto = StringValue.PROTOTYPE;

    private final SMOPolicy smoPolicy = new NonThrashingSMOPolicy(0.2, 0.2);

    private PageStorage pageStorage;
    private final Transaction<IntegerKey, StringValue> tx = new DummyTransaction<IntegerKey, StringValue>(
        TEST_OWNER);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        dataBytes = new byte[PAGE_SIZE];
        data = ByteBuffer.wrap(dataBytes);
        pageStorage = new MemoryPageStorage(PAGE_SIZE);
        tree = createTree();
        ops = tree.getOperations();
    }

    private BTreePage<IntegerKey, StringValue> createPage() {
        BTreePage<IntegerKey, StringValue> page = new BTreePage<IntegerKey, StringValue>(tree,
            new PageID(10), PAGE_SIZE);
        page.formatNewPage(data);
        page.format(1);
        PagePath<IntegerKey, StringValue, BTreePage<IntegerKey, StringValue>> path = new PagePath<IntegerKey, StringValue, BTreePage<IntegerKey, StringValue>>(
            page, true);

        ops.insert(path, new IntegerKey(3), new StringValue("V3"), tx);
        ops.insert(path, new IntegerKey(6), new StringValue("V6"), tx);
        return page;
    }

    public void testPageWriting() {
        BTreePage<IntegerKey, StringValue> page = createPage();
        data.rewind();
        page.savePageData();
        assertEquals(76, data.position());

        data.rewind();
        assertEquals(0, data.position());

        // Check page type
        int pageType = data.getInt();
        assertEquals(page.getTypeIdentifier(), pageType);

        // Check setting bits
        /* int settings = */data.getInt();

        // Check height
        assertEquals(page.getHeight(), data.getInt());

        // Check entry count
        assertEquals(page.getEntryCount(), data.getInt());

        // Step 2. Check page key range + next page if
        assertEquals(Integer.MIN_VALUE, data.getInt());
        assertEquals(Integer.MAX_VALUE, data.getInt());
        // Next page id
        assertEquals(-1, data.getInt());

        // Step 3. Check entries

        int offset = data.position();
        // First item
        // The key stored only once
        assertEquals(3, data.getInt());
        // Value
        assertEquals("V3", ByteUtils.readString(data, valueProto.getByteDataSize(), valueProto
            .getEncodingCharset()));

        offset = offset + page.getSingleEntrySize();
        data.position(offset);
        // Second item
        // The key stored only once
        assertEquals(6, data.getInt());
        // Value
        assertEquals("V6", ByteUtils.readString(data, valueProto.getByteDataSize(), valueProto
            .getEncodingCharset()));
    }

    public void testPageReading() {
        BTreePage<IntegerKey, StringValue> page = createPage();
        data.rewind();
        page.savePageData();

        BTreePage<IntegerKey, StringValue> page2 = new BTreePage<IntegerKey, StringValue>(tree,
            new PageID(10), PAGE_SIZE);
        page2.loadPageData(data);

        assertEquals(10, page.getPageID().intValue());
        assertEquals(2, page.getContents().size());

        assertEquals("V3", page.getEntry(new IntegerKey(3)).getValue());
        assertEquals("V6", page.getEntry(new IntegerKey(6)).getValue());
    }

    protected BTree<IntegerKey, StringValue> createTree() {
        return new BTreeDatabase<IntegerKey, StringValue>(32, smoPolicy, IntegerKey.PROTOTYPE,
            StringValue.PROTOTYPE, pageStorage).getDatabaseTree();
    }
}
