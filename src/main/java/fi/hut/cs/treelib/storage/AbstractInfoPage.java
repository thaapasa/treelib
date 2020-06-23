package fi.hut.cs.treelib.storage;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.PageID;

public abstract class AbstractInfoPage<P extends AbstractInfoPage<P>> extends AbstractPage
    implements PageFactory<P>, Component {

    private PageID rootPageID;
    private final int pageTypeID;

    protected AbstractInfoPage(int pageSize, int pageTypeID) {
        super(PageID.INVALID_PAGE_ID, pageSize);
        this.pageTypeID = pageTypeID;
    }

    public AbstractInfoPage(PageID pageID, int pageSize, int pageTypeID) {
        super(pageID, pageSize);
        this.pageTypeID = pageTypeID;
        // Default root page ID (uninitialized)
        rootPageID = PageID.INVALID_PAGE_ID;
    }

    public final int getTypeIdentifier() {
        return pageTypeID;
    }

    public PageID getRootPageID() {
        return rootPageID;
    }

    public void setRootPageID(PageID rootPageID) {
        if (!this.rootPageID.equals(rootPageID)) {
            // Set dirty only if data changes
            setDirty(true);
        }
        this.rootPageID = rootPageID;
    }

    @Override
    protected boolean loadPageDataImpl(ByteBuffer pageData) {
        // First word in page is the page type
        int pageType = pageData.getInt();

        // Page has not been initialized
        if (pageType != getTypeIdentifier())
            return false;

        rootPageID = PageID.PROTOTYPE.readFromBytes(pageData);
        return true;
    }

    @Override
    protected void savePageDataImpl(ByteBuffer pageData) {
        pageData.putInt(getTypeIdentifier());
        rootPageID.writeToBytes(pageData);
    }

    @Override
    public void checkConsistency(Object... params) {
    }

    @Override
    public void printDebugInfo() {
        System.out.println(this);
    }

    @Override
    public String toString() {
        return "Info page with type: 0x" + Integer.toHexString(pageTypeID) + ", root page id: "
            + rootPageID;
    }

}
