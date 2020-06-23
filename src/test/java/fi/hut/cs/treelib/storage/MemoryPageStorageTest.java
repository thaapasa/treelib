package fi.hut.cs.treelib.storage;

import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.TreeLibTest;
import fi.hut.cs.treelib.concurrency.NoopLatchManager;

public class MemoryPageStorageTest extends TreeLibTest {

    private final int pageSize = 100;

    public void testIntegerSave() {
        PageStorage storage = new MemoryPageStorage(pageSize);
        PageFactory<TestPage> factory = new TestPageFactory(pageSize);
        PageBuffer buffer = new PageBuffer(storage, 10, NoopLatchManager.instance());
        buffer.initialize();

        assertEquals(0, buffer.getTotalPageFixes());
        assertEquals(0, buffer.getPagesInBuffer());

        TestPage page = buffer.createPage(factory, TEST_OWNER);
        assertEquals(1, buffer.getTotalPageFixes());
        assertEquals(2, buffer.getPagesInBuffer());
        buffer.flush(true);
        assertEquals(1, buffer.getPagesInBuffer());

        PageID pid = page.getPageID();
        page.data = 0x00abcd00;
        page.setDirty(true);

        assertEquals(1, buffer.getTotalPageFixes());
        buffer.unfix(page, TEST_OWNER);
        assertEquals(0, buffer.getTotalPageFixes());

        buffer.flush(true);
        assertEquals(0, buffer.getPagesInBuffer());

        page = buffer.fixPage(pid, factory, false, TEST_OWNER);
        assertEquals(1, buffer.getPagesInBuffer());
        assertEquals(0x00abcd00, page.data);
        buffer.unfix(page, TEST_OWNER);
    }

    public void testPageBufferPageFixes() {
        PageStorage storage = new MemoryPageStorage(pageSize);
        PageFactory<TestPage> factory = new TestPageFactory(pageSize);
        PageBuffer buffer = new PageBuffer(storage, 5, NoopLatchManager.instance());
        buffer.initialize();

        TestPage page = buffer.fixPage(new PageID(1), factory, true, TEST_OWNER);
        page.data = 0x00abcd00;

        for (int i = 2; i < 6; i++) {
            buffer.fixPage(new PageID(i), factory, true, TEST_OWNER);
            assertTrue(buffer.containsPage(new PageID(1)));
        }

        try {
            buffer.fixPage(new PageID(6), factory, true, TEST_OWNER);
            fail("Buffer full but no exception thrown");
        } catch (IndexOutOfBoundsException e) {
            // OK, buffer is full
        }

    }

    public void testPageBufferPageLoads() {
        PageStorage storage = new MemoryPageStorage(pageSize);
        PageFactory<TestPage> factory = new TestPageFactory(pageSize);
        PageBuffer buffer = new PageBuffer(storage, 5, NoopLatchManager.instance());
        buffer.initialize();

        for (int i = 1; i < 20; i++) {
            TestPage page = buffer.fixPage(new PageID(i), factory, true, TEST_OWNER);
            page.data = 0x00abcd00 + i;
            page.setDirty(true);
            assertTrue(buffer.containsPage(new PageID(i)));
            buffer.unfix(page, TEST_OWNER);
        }

        for (int i = 1; i < 20; i++) {
            TestPage page = buffer.fixPage(new PageID(i), factory, false, TEST_OWNER);
            assertEquals(0x00abcd00 + i, page.data);
            buffer.unfix(page, TEST_OWNER);
        }

    }
}
