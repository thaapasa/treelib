package fi.hut.cs.treelib.storage;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.PageID;

/**
 * Generic interface for stored data.
 * 
 * @author tuska
 */
public interface StoredPage {

    PageID getPageID();

    /**
     * Type identifier is stored at the beginning of the page.
     * 
     * @return an integer value that distinguishes this page type from other
     * pages
     */
    int getTypeIdentifier();

    /**
     * @return the page size, in bytes
     */
    int getPageSize();

    boolean isDirty();

    void setDirty(boolean state);

    /**
     * Formats this page as a new page.
     * 
     * @param pageData the data area that is used to store the page contents
     */
    void formatNewPage(ByteBuffer pageData);

    /**
     * Loads the page from the given byte array.
     * 
     * @param data the page data. Page updates must be done to this byte array
     * @return true if the data was loaded successfully
     */
    boolean loadPageData(ByteBuffer data);

    /**
     * Saves the page data. This function must flush the page updates (if any
     * are pending) to the byte array given in loadPageData.
     */
    void savePageData();

    /**
     * Signals that the given page data is no longer used by this page. After
     * this call, the page must not make modifications to the byte buffer.
     */
    void releasePageData();

}
