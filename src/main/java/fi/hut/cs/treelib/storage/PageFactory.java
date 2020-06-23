package fi.hut.cs.treelib.storage;

import fi.hut.cs.treelib.PageID;

/**
 * Factory class interface for creating empty pages.
 * 
 * @author thaapasa
 */
public interface PageFactory<P extends StoredPage> {

    /**
     * Creates an empty page. A proper formatting function will be called
     * after the page has been created.
     * 
     * @param pageID the page ID.
     * @return the newly created, uninitialized page.
     */
    P createEmptyPage(PageID pageID);

    int getTypeIdentifier();

}
