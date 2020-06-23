package fi.hut.cs.treelib.storage;

import java.io.IOException;
import java.nio.ByteBuffer;

import fi.hut.cs.treelib.PageID;

public interface PageStorage {

    /**
     * Initializes the connection to the page storage.
     */
    void initialize() throws IOException;

    /**
     * Checks if this store has been initialized
     */
    boolean isInitialized();

    /**
     * Closes the connection to the storage. No more functions will be called.
     */
    void close() throws IOException;

    /**
     * Clear all pages from the page storage.
     */
    boolean clear();

    /**
     * @return the page size
     */
    int getPageSize();

    /**
     * Saves the given page data to the page storage.
     * 
     * @param bufferPage the buffer page, which must be stored to the external
     * storage
     */
    void savePage(PageID pageID, ByteBuffer bufferPage) throws IOException;

    /**
     * Loads the page with the given page ID from the page storage to the
     * given buffer space slot. If the page does not exist, this method must
     * clear the buffer page.
     * 
     * @param bufferPage the buffer page, into which the data is read
     */
    void loadPage(PageID pageID, ByteBuffer bufferPage) throws IOException;

    /**
     * Deletes the given page and deallocates the space from the page storage.
     */
    void deletePage(PageID pageID) throws IOException;

    /**
     * Return the id of the last page currently addressable with the space
     * allocation maps. That is, if a page larger than this is created, a new
     * space allocation map must be created.
     * 
     * @return the id of the last page currently handled; or -1, if no pages
     * are allocated
     */
    PageID getMaxPageID();

    boolean containsPage(PageID pageID);

    void reservePageID(PageID pageID);

}
