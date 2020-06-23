package fi.hut.cs.treelib.storage;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.PageID;

/**
 * Stores the pages in a file.
 * 
 * @author thaapasa
 */
public class FilePageStorage implements PageStorage {

    private static final Logger log = Logger.getLogger(FilePageStorage.class);

    private static final String FILE_MODE = "rw";
    private final File fileName;
    private RandomAccessFile file = null;

    private final int pageSize;

    public FilePageStorage(int pageSize, File fileName) {
        this.fileName = fileName;
        this.pageSize = pageSize;
    }

    public File getFile() {
        return fileName;
    }

    @Override
    public void deletePage(PageID pageID) {
        // Nothing to do
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    private void seekToPage(PageID pageID) throws IOException {
        assert isInitialized();
        assert pageID != null;
        int pageIndex = pageID.intValue() * pageSize;
        file.seek(pageIndex);
    }

    /**
     * Called from page factory to load a page
     */
    @Override
    public synchronized void loadPage(PageID pageID, ByteBuffer bufferPage) throws IOException {
        assert isInitialized();
        log.debug(String.format("Loading page %s from %s", pageID, this));
        assert pageSize == bufferPage.capacity() : pageSize + " != " + bufferPage.capacity();
        seekToPage(pageID);
        try {
            file.readFully(bufferPage.array());
        } catch (EOFException e) {
            // File not initialized yet. Fill with zeros.
            byte[] arr = bufferPage.array();
            for (int i = 0; i < arr.length; i++) {
                arr[i] = 0;
            }
        }
    }

    @Override
    public synchronized void savePage(PageID pageID, ByteBuffer bufferPage) throws IOException {
        assert isInitialized();
        log.debug(String.format("Saving page %s to %s", pageID, this));
        assert pageSize == bufferPage.capacity() : pageSize + " != " + bufferPage.capacity();
        seekToPage(pageID);
        file.write(bufferPage.array());
    }

    @Override
    public void close() throws IOException {
        if (!isInitialized())
            return;
        file.close();
        file = null;
    }

    @Override
    public void initialize() throws IOException {
        assert file == null;
        // Open file
        file = new RandomAccessFile(fileName, FILE_MODE);
        assert file != null;
    }

    @Override
    public boolean isInitialized() {
        return file != null;
    }

    @Override
    public String toString() {
        return String.format("File storage, page size %d bytes, file %s", pageSize, fileName);
    }

    @Override
    public PageID getMaxPageID() {
        assert isInitialized();
        try {
            long fileSize = file.length();
            int pgCount = (int) (fileSize / pageSize);
            assert fileSize % pageSize == 0 : fileSize + " is not exactly divisible by "
                + pageSize;
            // Id of last page is the page count - 1
            return new PageID(pgCount - 1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean containsPage(PageID pageID) {
        assert isInitialized();
        return pageID.intValue() <= getMaxPageID().intValue();
    }

    @Override
    public boolean clear() {
        assert isInitialized();
        try {
            file.seek(0);
            file.setLength(0);
        } catch (IOException e) {
            log.warn("Could not clear database file: " + e.getMessage(), e);
            return false;
        }
        return true;
    }

    @Override
    public void reservePageID(PageID pageID) {
        // Nothing required
    }
}
