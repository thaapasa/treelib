package fi.hut.cs.treelib.rtree;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.storage.AbstractInfoPage;

public class RTreeInfoPage<K extends Key<K>> extends AbstractInfoPage<RTreeInfoPage<K>> {

    public static final int PAGE_IDENTIFIER = 0xa871f0;

    private final MBR<K> mbrProto;
    private MBR<K> extents;

    protected RTreeInfoPage(int pageSize, MBR<K> mbrProto) {
        super(pageSize, PAGE_IDENTIFIER);
        this.mbrProto = mbrProto;
    }

    public RTreeInfoPage(PageID pageID, int pageSize, MBR<K> mbrProto) {
        super(pageID, pageSize, PAGE_IDENTIFIER);
        this.mbrProto = mbrProto;
    }

    public MBR<K> getExtents() {
        return extents;
    }

    public void setExtents(MBR<K> extents) {
        this.extents = extents;
        setDirty(true);
    }

    @Override
    public RTreeInfoPage<K> createEmptyPage(PageID pageID) {
        return new RTreeInfoPage<K>(pageID, getPageSize(), mbrProto);
    }

    @Override
    protected boolean loadPageDataImpl(ByteBuffer pageData) {
        boolean dataOk = super.loadPageDataImpl(pageData);
        if (!dataOk)
            return false;

        extents = mbrProto.readFromBytes(pageData);
        return true;
    }

    @Override
    protected void savePageDataImpl(ByteBuffer pageData) {
        super.savePageDataImpl(pageData);
        if (extents != null)
            extents.writeToBytes(pageData);
        else
            mbrProto.writeToBytes(pageData);
    }

}
