package fi.hut.cs.treelib.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.concurrency.LatchManager;
import fi.hut.cs.treelib.stats.NoStatistics;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.AssertionSupport;
import fi.tuska.util.Callback;
import fi.tuska.util.FixableLeakingMap;
import fi.tuska.util.Holder;
import fi.tuska.util.Pair;

/**
 * Page buffer reserves pages 0, x, 2x, 3x etc. for storing the
 * PageAllocationMaps. The variable x is the size of the page allocation map
 * (e.g., page size * 8). Therefore, if page size is 4096, pages 0, 4096,
 * 8192, etc. are reserved. All other pages are free for use.
 * 
 * <p>
 * The page buffer also contains convenience methods for using the latch
 * manager to latch and unlatch pages.
 * 
 * @author thaapasa
 */
public class PageBuffer implements Component {

    /**
     * Debugging: set to true to force the writing of all pages (not just
     * dirtied pages)
     */
    private static final boolean WRITE_CLEAN_PAGES = false;

    private static final Owner BUFFER_INTERNAL_OWNER = new OwnerImpl("PageBuffer-internal");

    private final int bufferSize;
    private final int pageSize;
    private final PageStorage storage;
    /** Key: page id, Contents: page and buffer position index */
    private final FixableLeakingMap<PageID, Pair<StoredPage, Integer>> pageBuffer;
    private final LinkedList<Integer> freeBufferList = new LinkedList<Integer>();
    private final PageFactory<PageAllocationMap> mapFactory;
    private final byte[][] bufferData;
    private final ByteBuffer[] buffer;
    private final LatchManager latchManager;
    private static final Logger log = Logger.getLogger(PageBuffer.class);

    private static final Map<Integer, PageFactory<?>> registeredFactories = new HashMap<Integer, PageFactory<?>>();

    /** Listeners for page flush events. */
    private List<PageFlushListener> flushListeners = new ArrayList<PageFlushListener>(3);

    private StatisticsLogger stats = NoStatistics.instance();

    public PageBuffer(PageStorage storage, int bufferSize, LatchManager latchManager) {
        this.bufferSize = bufferSize;
        this.pageSize = storage.getPageSize();
        this.storage = storage;
        this.mapFactory = new PageAllocationMapFactory(pageSize);
        this.bufferData = new byte[bufferSize][pageSize];
        this.buffer = new ByteBuffer[bufferSize];
        this.latchManager = latchManager;
        // Create the page buffer
        this.pageBuffer = new FixableLeakingMap<PageID, Pair<StoredPage, Integer>>(bufferSize);
        this.pageBuffer
            .addLeakEventListener(new FixableLeakingMap.LeakEventListener<PageID, Pair<StoredPage, Integer>>() {
                @Override
                public void itemLeaked(PageID pageID, Pair<StoredPage, Integer> data) {
                    StoredPage page = data.getFirst();
                    int bufferIndex = data.getSecond();
                    if (log.isDebugEnabled())
                        log.debug("Leaking page " + pageID + " at buffer id " + bufferIndex);
                    flushPage(page, bufferIndex);
                    freeBufferList.add(bufferIndex);
                }
            });

        registerPageFactory(mapFactory);
        clearStructures();
        log.info(String.format("Created page buffer with storage: %s", storage));
    }

    public static void registerPageFactory(PageFactory<?> factory) {
        registeredFactories.put(factory.getTypeIdentifier(), factory);
    }

    public void registerPageFlushListener(PageFlushListener listener) {
        flushListeners.add(listener);
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void clearStructures() {
        freeBufferList.clear();
        pageBuffer.clear();
        // Initialize buffer pages
        // Mark the pages as free, and allocate ByteBuffer objects
        for (int i = 0; i < bufferSize; i++) {
            // Add all pages to the free buffer page list
            freeBufferList.addLast(i);
            this.buffer[i] = ByteBuffer.wrap(this.bufferData[i]);
            assert this.buffer[i].capacity() == pageSize;
            assert this.buffer[i].remaining() == pageSize;
        }
    }

    public boolean clear() {
        if (getTotalPageFixes() > 0) {
            return false;
        }

        storage.clear();
        clearStructures();
        return true;
    }

    public void setStatisticsLogger(StatisticsLogger stats) {
        this.stats = stats;
    }

    public void initialize() {
        try {
            storage.initialize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isInitialized() {
        return storage.isInitialized();
    }

    public PageStorage getPageStorage() {
        return storage;
    }

    public String getPageFixSummary() {
        return pageBuffer.getEntryFixSummary();
    }

    public boolean containsPage(PageID pageID) {
        return pageBuffer.contains(pageID);
    }

    /**
     * This can be used to check that all the pages have been released, for
     * example between operations when running in single-thread mode.
     * 
     * @return the total amount of page fixes (if a page has two fixes, that
     * counts as two)
     */
    public int getTotalPageFixes() {
        return pageBuffer.getTotalFixCount();
    }

    public int getPagesInBuffer() {
        return pageBuffer.getSize();
    }

    public synchronized void flush(boolean removeUnfixed) {
        log.debug("Flushing entire page buffer");
        List<Pair<StoredPage, Integer>> removePages = null;
        if (removeUnfixed)
            removePages = new ArrayList<Pair<StoredPage, Integer>>(pageBuffer.getSize());

        for (PageID pageID : pageBuffer.keySet()) {
            Pair<StoredPage, Integer> data = pageBuffer.get(pageID);
            assert !freeBufferList.contains(data.getSecond()) : data;

            StoredPage page = data.getFirst();
            int bufferIndex = data.getSecond();

            flushPage(page, bufferIndex);
            pageBuffer.unfix(pageID);

            if (removeUnfixed && !pageBuffer.isFixed(pageID))
                removePages.add(data);
        }

        if (removeUnfixed) {
            log.debug("Removing unfixed pages");
            // Remove all unfixed pages in the pageIDs array
            for (Pair<StoredPage, Integer> data : removePages) {
                StoredPage page = data.getFirst();
                int bufferIndex = data.getSecond();

                assert !pageBuffer.isFixed(page.getPageID()) : page.getPageID();

                int s = pageBuffer.getSize();
                removePage(page, bufferIndex);
                assert pageBuffer.getSize() == s - 1 : "Total space should be " + (s - 1)
                    + ", is " + pageBuffer.getSize();
            }
        }
    }

    /**
     * Flushes the given page to page buffer and to the page storage.
     */
    public synchronized void flushPage(StoredPage page, int bufferIndex) {
        if (log.isDebugEnabled())
            log.debug(String.format("Flushing page %s", page.getPageID()));

        // Inform listeners that page is about to be flushed
        for (PageFlushListener listener : flushListeners) {
            listener.prepareForFlush(page);
        }

        if (WRITE_CLEAN_PAGES || page.isDirty()) {
            // Flush page data to buffer page (if it's not already updated)
            buffer[bufferIndex].rewind();
            page.savePageData();
            page.setDirty(false);

            // Store buffer page data to storage area
            try {
                stats.log(Operation.OP_BUFFER_WRITE);
                buffer[bufferIndex].rewind();
                storage.savePage(page.getPageID(), buffer[bufferIndex]);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized void close() throws IOException {
        flush(false);
        storage.close();
    }

    /**
     * For debugging, prints the fix state of the map.
     */
    @Override
    public synchronized void printDebugInfo() {
        pageBuffer.printFixes(System.out);
        int totalPages = countUsedPages();
        System.out.println("Total pages in storage: " + totalPages);
        if (totalPages < 10) {
            traverseAllocationMaps(new Callback<PageAllocationMap>() {
                @Override
                public boolean callback(PageAllocationMap map) {
                    map.printDebugInfo();
                    return true;
                }
            }, BUFFER_INTERNAL_OWNER);
        }
    }

    public int countUsedPages() {
        final Holder<Integer> count = new Holder<Integer>(0);
        traverseAllocationMaps(new Callback<PageAllocationMap>() {
            @Override
            public boolean callback(PageAllocationMap map) {
                count.setValue(count.getValue() + map.getPageCount());
                return true;
            }
        }, BUFFER_INTERNAL_OWNER);
        return count.getValue();
    }

    /**
     * Fixes a page to the buffer. The page is either found from the buffer,
     * or loaded from the external page storage. Call unfix() to release the
     * page after use.
     * 
     * @param pageID the page ID
     * @return the fixed page
     */
    @SuppressWarnings("unchecked")
    public synchronized <T extends StoredPage> T fixPage(PageID pageID, PageFactory<T> factory,
        boolean createNewPage, Owner owner) {
        if (log.isDebugEnabled())
            log.debug(String.format("Fixing page %s to page buffer", pageID));

        stats.log(Operation.OP_BUFFER_FIX);
        // pageBuffer.get() fixes the page
        Pair<StoredPage, Integer> data = pageBuffer.get(pageID);
        if (data != null) {
            // Page found from buffer
            StoredPage page = data.getFirst();
            if (log.isDebugEnabled())
                logOperation("Found", pageID);
            return (T) page;
        }
        return loadPageFromStorage(pageID, factory, createNewPage);
    }

    /**
     * Fix an existing page. Reads the page type from the stored page.
     * 
     * @param pageID the page to fix
     * @return the fixed page; or null, if no such page exists
     */
    public synchronized StoredPage fixPage(PageID pageID, Owner owner) {
        if (log.isDebugEnabled())
            log.debug(String.format("Fixing page %s to page buffer", pageID));

        stats.log(Operation.OP_BUFFER_FIX);
        // pageBuffer.get() fixes the page
        Pair<StoredPage, Integer> data = pageBuffer.get(pageID);
        if (data != null) {
            // Page found from buffer
            StoredPage page = data.getFirst();
            if (log.isDebugEnabled())
                logOperation("Found", pageID);
            return page;
        }
        return loadPageFromStorage(pageID, null, false);
    }

    /**
     * @return the newly created page, with one fix to the buffer.
     */
    public synchronized <T extends StoredPage> T createPage(PageFactory<T> factory, Owner owner) {
        PageID newPageID = reserveNewPageID(owner);

        stats.log(Operation.OP_SPACEMAP_ALLOCATE);
        pageBuffer.ensureCapacity();

        // Load the page data
        int bufferID = 0;
        try {
            bufferID = loadPageIntoBuffer(newPageID);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Create empty page
        final T page = factory.createEmptyPage(newPageID);
        // Format the page as a new page
        buffer[bufferID].clear();
        page.formatNewPage(buffer[bufferID]);
        // New pages are dirty so they are saved
        page.setDirty(true);

        // Add the page to the buffer
        pageBuffer.put(newPageID, new Pair<StoredPage, Integer>(page, bufferID));

        if (log.isDebugEnabled())
            logOperation("Created", newPageID);
        return page;
    }

    /**
     * Requires that the page is fixed, and that no other thread has a fix on
     * it.
     */
    public synchronized void delete(StoredPage page, Owner owner) {
        delete(page.getPageID(), owner);
    }

    private void clearInternal(PageID pageID) {
        Pair<StoredPage, Integer> data = pageBuffer.get(pageID);
        // Two unfixes: one for the previous get, one for the caller page fix
        pageBuffer.unfix(pageID);
        pageBuffer.unfix(pageID);
        // Remove the page from page buffer
        removePage(data.getFirst(), data.getSecond());

        try {
            storage.deletePage(pageID);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void clear(PageID pageID, Owner owner) {
        int fixCount = pageBuffer.getFixCount(pageID);
        if (fixCount != 1)
            throw new IllegalStateException("Clear() requires exactly one fix on the page (was: "
                + fixCount + ")");

        stats.log(Operation.OP_SPACEMAP_CLEAR);
        clearInternal(pageID);
    }

    public synchronized void delete(PageID pageID, Owner owner) {
        int fixCount = pageBuffer.getFixCount(pageID);
        if (fixCount != 1)
            throw new IllegalStateException(
                "Delete() requires exactly one fix on the page (was: " + fixCount + ")");

        stats.log(Operation.OP_SPACEMAP_FREE);
        clearInternal(pageID);

        // Delete the page from the page allocation map
        PageAllocationMap map = getPageAllocationMap(pageID, owner);
        map.release(pageID);
        unfix(map, owner);
    }

    public synchronized void readLatch(PageID pageID, Owner owner) {
        latchManager.readLatch(pageID, owner);
    }

    public synchronized void writeLatch(PageID pageID, Owner owner) {
        latchManager.writeLatch(pageID, owner);
    }

    public synchronized void unlatch(StoredPage page, Owner owner) {
        latchManager.unlatch(page.getPageID(), owner);
    }

    public synchronized void unlatch(PageID pageID, Owner owner) {
        latchManager.unlatch(pageID, owner);
    }

    public void disposePages(Collection<PageID> pages, boolean unlatch, boolean unfix, Owner owner) {
        disposePages(pages, unlatch, unfix, null, owner);
    }

    /**
     * Call to dispose of an array of page IDs. The collection will be
     * cleared.
     * 
     * @param pages the page IDs
     * @param unlatch true to unlatch all the page IDs
     * @param unfix true to unfix all the page IDs
     * @param exceptThisPage for enhancements: if this pageID is encountered,
     * it will not be touched
     */
    public void disposePages(Collection<PageID> pages, boolean unlatch, boolean unfix,
        PageID exceptThisPage, Owner owner) {
        for (PageID pageID : pages) {
            if (exceptThisPage != null && exceptThisPage.equals(pageID))
                continue;

            if (unlatch)
                unlatch(pageID, owner);
            if (unfix)
                unfix(pageID, owner);
        }
        pages.clear();
    }

    /**
     * Clears the given page path, unfixing all pages on it and removing them
     * from the path.
     */
    public synchronized <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> void unfix(
        PagePath<K, V, P> path, Owner owner) {
        if (path == null)
            return;
        while (!path.isEmpty()) {
            P page = path.getCurrent();
            unfix(page, owner);
            path.ascend();
        }
    }

    /**
     * @param page page to unfix. Can be null, in which case the call is
     * ignored.
     */
    public synchronized void unfix(StoredPage page, Owner owner) {
        if (page == null) {
            return;
        }
        unfix(page.getPageID(), owner);
    }

    public synchronized void unfix(PageID pageID, Owner owner) {
        if (pageID == null) {
            return;
        }
        pageBuffer.unfix(pageID);
        if (log.isDebugEnabled())
            logOperation("Unfixed", pageID);
    }

    /**
     * Assures that a page ID is reserved.
     * 
     * @param pageID the page ID that should be reserved.
     * @return true if the page id was free (and is now reserved); or false,
     * if the page was already reserved
     */
    public boolean reservePageID(PageID pageID) {
        PageAllocationMap map = getPageAllocationMap(pageID, BUFFER_INTERNAL_OWNER);
        boolean wasFree = false;
        if (map.isFree(pageID)) {
            map.reserve(pageID);
            wasFree = true;
        }
        unfix(map, BUFFER_INTERNAL_OWNER);
        storage.reservePageID(pageID);
        return wasFree;
    }

    @Override
    public String toString() {
        return String.format("Pagebuffer, size: %d/%d (fixes: %s, free: %d=%d) (page size: %d)",
            pageBuffer.getSize(), pageBuffer.getMaximumSize(), getTotalPageFixes(), pageBuffer
                .getFreeSlots(), freeBufferList.size(), pageSize);
    }

    private void removePage(StoredPage page, int bufferIndex) {
        assert !freeBufferList.contains(bufferIndex) : "Buffer index " + bufferIndex
            + " already free, page " + page;
        pageBuffer.remove(page.getPageID());
    }

    @SuppressWarnings("unchecked")
    private <T extends StoredPage> T loadPageFromStorage(PageID pageID, PageFactory<T> factory,
        boolean createNewPage) {
        if (!createNewPage && !storage.containsPage(pageID))
            return null;

        stats.log(Operation.OP_BUFFER_READ);
        pageBuffer.ensureCapacity();
        // ensureCapacity() might actually load the page
        Pair<StoredPage, Integer> data = pageBuffer.get(pageID);
        if (data != null) {
            return (T) data.getFirst();
        }

        int bufferID = 0;
        try {
            bufferID = loadPageIntoBuffer(pageID);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (factory == null) {
            // Peek the page type from the page data
            ByteBuffer pageBuf = buffer[bufferID];
            int pageType = pageBuf.getInt();
            pageBuf.rewind();

            if (pageType == 0) {
                assert createNewPage;
                // Can't create page because page factory is not defined
                // TODO: Should we throw an exception here?
                throw new UnsupportedOperationException(
                    "Factory not defined, cannot create new page");
            }
            factory = (PageFactory<T>) registeredFactories.get(pageType);
            if (factory == null) {
                throw new UnsupportedOperationException("Page type "
                    + Integer.toHexString(pageType) + " not recognized");
            }
        }

        // Create empty page
        final T page = factory.createEmptyPage(pageID);

        // Load page from buffer page
        buffer[bufferID].rewind();
        if (!page.loadPageData(buffer[bufferID])) {
            if (!createNewPage) {
                throw new UnsupportedOperationException("Page data is invalid for page " + pageID
                    + " and not allowed to create a new page");
            }
            page.formatNewPage(buffer[bufferID]);
        }

        // Add the page to the page buffer
        pageBuffer.put(pageID, new Pair<StoredPage, Integer>(page, bufferID));

        if (log.isDebugEnabled())
            logOperation("Loaded", pageID);
        return page;
    }

    /**
     * Finds (and reserves) a free page ID. Fixes the space allocation map (or
     * possibly a couple of maps) to find the new page ID.
     * 
     * @return the new page ID
     */
    public PageID reserveNewPageID(Owner owner) {
        int tryRange = 0;
        while (true) {
            PageAllocationMap map = getPageAllocationMap(new PageID(tryRange), owner);
            PageID pageID = map.findAndReserve();
            unfix(map, owner);

            if (pageID != null) {
                return pageID;
            }
            // No free space found on this space section, skip to next section
            // Section size is pageSize * 8 pages
            tryRange += pageSize * 8;
        }
    }

    /**
     * @return the page allocation map, fixed, for the given page ID. Release
     * (unfix) after use!
     */
    private PageAllocationMap getPageAllocationMap(PageID pageID, Owner owner) {
        int spaceSection = pageID.intValue() / (pageSize * 8);
        PageID spaceSectionID = new PageID(spaceSection * pageSize * 8);
        PageAllocationMap map = fixPage(spaceSectionID, mapFactory, true, owner);
        return map;
    }

    /**
     * @return the index of the buffer slot used to load the page. Needs to be
     * inserted to pageBuffer so that the slot will be freed!
     */
    private int loadPageIntoBuffer(PageID pageID) throws IOException {
        if (log.isDebugEnabled())
            log.debug(String.format("Loading page %s from %s", pageID, storage));

        // Ensure capacity for adding one more item. May cause a page to flush
        // out. This will call the itemLeaked() -method.
        // pageBuffer.ensureCapacity();

        if (log.isDebugEnabled())
            log.debug("Buffer: " + this);

        assert !freeBufferList.isEmpty() : "No free buffers when fixing page " + pageID + ": "
            + freeBufferList + "; " + this;
        final int bufferIndex = freeBufferList.removeFirst();

        // Load contents from storage to local buffer
        buffer[bufferIndex].rewind();
        storage.loadPage(pageID, buffer[bufferIndex]);

        return bufferIndex;
    }

    private void logOperation(String oper, PageID pageID) {
        if (log.isDebugEnabled()) {
            if (AssertionSupport.isAssertionsEnabled())
                log.debug(String.format("%s page %s (%s), free: %d=%d", oper, pageID,
                    getPageFixSummary(), pageBuffer.getFreeSlots(), freeBufferList.size()));
            else
                log.debug(String.format("%s page %s, free: %d=%d", oper, pageID, pageBuffer
                    .getFreeSlots(), freeBufferList.size()));
        }
        // assert pageBuffer.getFreeSlots() == freeBufferList.size();
    }

    public int getTotalPageCount() {
        final Holder<Integer> pageCount = new Holder<Integer>(0);
        traverseAllocationMaps(new Callback<PageAllocationMap>() {
            @Override
            public boolean callback(PageAllocationMap map) {
                pageCount.setValue(pageCount.getValue() + map.getPageCount());
                return true;
            }
        }, BUFFER_INTERNAL_OWNER);
        return pageCount.getValue();
    }

    @Override
    public void checkConsistency(Object... params) {
        // No consistency checks for now
    }

    /**
     * Loops through all space allocation maps used by the database system
     */
    private void traverseAllocationMaps(Callback<PageAllocationMap> callback, Owner owner) {
        int cur = 0;
        int max = storage.getMaxPageID().intValue();
        while (cur <= max) {
            PageAllocationMap map = getPageAllocationMap(new PageID(cur), owner);
            callback.callback(map);
            cur = map.getMaxPageID().intValue() + 1;
            unfix(map, owner);
        }
    }

}
