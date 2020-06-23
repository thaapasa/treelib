package fi.hut.cs.treelib.storage;

import java.nio.ByteBuffer;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.PageID;

/**
 * Stores the pages in memory.
 * 
 * @author thaapasa
 */
public class MemoryPageStorage implements PageStorage {

    private static final Logger log = Logger.getLogger(MemoryPageStorage.class);

    private SortedMap<PageID, byte[]> pages = new TreeMap<PageID, byte[]>();

    private boolean initialized = false;
    private final int pageSize;

    public MemoryPageStorage(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void deletePage(PageID pageID) {
        assert isInitialized();
        pages.remove(pageID);
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    private byte[] getBufferPage(PageID pageID) {
        assert isInitialized();
        byte[] localData = pages.get(pageID);
        if (localData == null) {
            localData = new byte[pageSize];
            pages.put(pageID, localData);
        }
        return localData;
    }

    /**
     * Called from page factory to load a page
     */
    @Override
    public void loadPage(PageID pageID, ByteBuffer bufferPage) {
        assert isInitialized();
        log.debug(String.format("Loading page %s from %s", pageID, this));
        assert pageSize == bufferPage.capacity() : pageSize + " != " + bufferPage.capacity();
        byte[] localData = pages.get(pageID);
        if (localData != null) {
            log.debug(String.format("Loading page %s data from memory storage", pageID));
            // Copy page data to buffer page
            System.arraycopy(localData, 0, bufferPage.array(), 0, pageSize);
        } else {
            // This creates the local buffer page. Might not actually be
            // necessary.
            // localData = getBufferPage(pageID);
            log.debug(String.format("Page %s does not yet exist, clearing buffer page", pageID));
            // New page, clear it to zeroes
            byte[] arr = bufferPage.array();
            for (int i = 0; i < arr.length; i++)
                arr[i] = 0;
        }
    }

    @Override
    public void savePage(PageID pageID, ByteBuffer bufferPage) {
        assert isInitialized();
        log.debug(String.format("Saving page %s to %s", pageID, this));
        assert pageSize == bufferPage.capacity() : pageSize + " != " + bufferPage.capacity();

        // Get reference to the local memory data storage area
        byte[] localData = getBufferPage(pageID);

        // Copy the buffer page to the local data storage
        System.arraycopy(bufferPage.array(), 0, localData, 0, pageSize);
    }

    @Override
    public void close() {
        // No need to do anything
    }

    @Override
    public void initialize() {
        initialized = true;
    }

    @Override
    public String toString() {
        return String.format("Memory storage, page size %d bytes (stored %d pages), id %d",
            pageSize, pages.size(), hashCode());
    }

    @Override
    public PageID getMaxPageID() {
        assert isInitialized();
        if (pages.isEmpty())
            return PageID.INVALID_PAGE_ID;
        return pages.lastKey();
    }

    @Override
    public boolean containsPage(PageID pageID) {
        assert isInitialized();
        assert pageID != null;
        return pages.containsKey(pageID);
    }

    @Override
    public boolean clear() {
        assert isInitialized();
        pages.clear();
        return true;
    }

    @Override
    public void reservePageID(PageID pageID) {
        getBufferPage(pageID);
    }

}
