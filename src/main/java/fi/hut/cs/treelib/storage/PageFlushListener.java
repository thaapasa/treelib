package fi.hut.cs.treelib.storage;

public interface PageFlushListener {

    /** Called from PageBuffer when a page is about to be flushed to disk. */
    void prepareForFlush(StoredPage page);

}
