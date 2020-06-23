package fi.hut.cs.treelib.btree;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.storage.AbstractInfoPage;

public class BTreeInfoPage extends AbstractInfoPage<BTreeInfoPage> {

    public static final int PAGE_IDENTIFIER = 0xb71f0;
    private byte[] extraData = new byte[0];

    protected BTreeInfoPage(int pageSize) {
        super(pageSize, PAGE_IDENTIFIER);
    }

    public BTreeInfoPage(PageID pageID, int pageSize) {
        super(pageID, pageSize, PAGE_IDENTIFIER);
    }

    @Override
    public BTreeInfoPage createEmptyPage(PageID pageID) {
        return new BTreeInfoPage(pageID, getPageSize());
    }

    public void setExtraData(byte[] data) {
        this.extraData = data != null ? data : new byte[0];
        setDirty(true);
    }

    public byte[] getExtraData() {
        return extraData;
    }

    @Override
    protected boolean loadPageDataImpl(ByteBuffer pageData) {
        if (!super.loadPageDataImpl(pageData))
            return false;

        int extraSize = pageData.getInt();
        extraData = new byte[extraSize];
        // TODO: May throw BufferOverflowException
        if (extraSize > 0) {
            pageData.get(extraData, 0, extraSize);
        }
        return true;
    }

    @Override
    protected void savePageDataImpl(ByteBuffer pageData) {
        super.savePageDataImpl(pageData);
        // TODO: Check length of extra data buffer
        pageData.putInt(extraData.length);
        if (extraData.length > 0) {
            pageData.put(extraData);
        }
    }
}
