package fi.hut.cs.treelib.storage;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.PageID;

public abstract class AbstractPage implements StoredPage {

    private final int pageSize;
    private final PageID pageID;
    private boolean dirty;
    private ByteBuffer pageData;

    public AbstractPage(PageID pageID, int pageSize) {
        this.pageID = pageID;
        assert pageID != null;
        this.dirty = true;
        this.pageSize = pageSize;
        this.pageData = null;
    }

    @Override
    public PageID getPageID() {
        return pageID;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void setDirty(boolean state) {
        this.dirty = state;
    }

    @Override
    public int getPageSize() {
        return this.pageSize;
    }

    @Override
    public void formatNewPage(ByteBuffer pageData) {
        assert this.pageData == null;
        this.pageData = pageData;
        setDirty(true);
    }

    protected abstract boolean loadPageDataImpl(ByteBuffer pageData);

    protected abstract void savePageDataImpl(ByteBuffer pageData);

    @Override
    public final boolean loadPageData(ByteBuffer pageData) {
        assert this.pageData == null;
        this.pageData = pageData;
        boolean success = loadPageDataImpl(this.pageData);
        if (!success) {
            this.pageData = null;
        }
        // Pages that have just been loaded are not dirty
        setDirty(false);
        return success;
    }

    @Override
    public final void savePageData() {
        assert this.pageData != null;
        savePageDataImpl(this.pageData);
    }

    @Override
    public final void releasePageData() {
        assert this.pageData != null;
        this.pageData = null;
    }

    @Override
    public int hashCode() {
        return pageID.intValue();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof AbstractPage))
            return false;

        AbstractPage p = (AbstractPage) o;
        return pageID.equals(p.pageID);
    }

}
