package fi.hut.cs.treelib.storage;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.TreeLibTest;
import fi.hut.cs.treelib.concurrency.NoopLatchManager;

public class PageBufferTest extends TreeLibTest {

    private static final Logger log = Logger.getLogger(PageBufferTest.class);

    private static final int PAGE_SIZE = 100;
    private static final int BUFFER_SIZE = 4;

    /*
     * This tests a bug found on 2009-08-03: a second page fix request from
     * inside a flush listener causes overwriting in page buffer (the second
     * request should find the page created by the first request).
     */
    public void testPageLeakLoadPageRecursive() {
        PageStorage st = new MemoryPageStorage(PAGE_SIZE);
        final PageBuffer buffer = new PageBuffer(st, BUFFER_SIZE, NoopLatchManager.instance());
        buffer.initialize();

        final PageFactory<TestPage> fac = new TestPageFactory(PAGE_SIZE);

        buffer.registerPageFlushListener(new PageFlushListener() {
            @Override
            public void prepareForFlush(StoredPage page) {
                TestPage tp = (TestPage) page;
                log.debug("Flushing page " + tp.getPageID());
                if (tp.getPageID().intValue() == 1 || tp.getPageID().intValue() == 2) {
                    log.debug("Loading extra page");
                    buffer.fixPage(new PageID(10), fac, true, TEST_OWNER);
                    buffer.fixPage(new PageID(11), fac, true, TEST_OWNER);
                    buffer.unfix(new PageID(11), TEST_OWNER);
                    buffer.unfix(new PageID(10), TEST_OWNER);
                }
            }
        });

        buffer.fixPage(new PageID(1), fac, true, TEST_OWNER);
        buffer.fixPage(new PageID(2), fac, true, TEST_OWNER);
        buffer.fixPage(new PageID(3), fac, true, TEST_OWNER);
        buffer.fixPage(new PageID(4), fac, true, TEST_OWNER);

        buffer.unfix(new PageID(1), TEST_OWNER);
        buffer.unfix(new PageID(2), TEST_OWNER);
        buffer.unfix(new PageID(3), TEST_OWNER);
        buffer.unfix(new PageID(4), TEST_OWNER);

        log.debug("-----");

        buffer.fixPage(new PageID(5), fac, true, TEST_OWNER);
        buffer.fixPage(new PageID(2), fac, true, TEST_OWNER);
        buffer.fixPage(new PageID(1), fac, true, TEST_OWNER);
        buffer.fixPage(new PageID(3), fac, true, TEST_OWNER);
    }

}
