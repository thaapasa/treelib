package fi.hut.cs.treelib.mdtree;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.storage.AbstractInfoPage;

/**
 * Info page for ordered multidimensional trees.
 * 
 * @author thaapasa
 */
public class OMDInfoPage<K extends Key<K>> extends AbstractInfoPage<OMDInfoPage<K>> {

    public static final int PAGE_IDENTIFIER = 0x03d71f0;

    private final MBR<K> mbrProto;
    private MBR<K> extents;

    protected OMDInfoPage(int pageSize, MBR<K> mbrProto) {
        super(pageSize, PAGE_IDENTIFIER);
        this.mbrProto = mbrProto;
    }

    public OMDInfoPage(PageID pageID, int pageSize, MBR<K> mbrProto) {
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
    public OMDInfoPage<K> createEmptyPage(PageID pageID) {
        return new OMDInfoPage<K>(pageID, getPageSize(), mbrProto);
    }

    @Override
    protected boolean loadPageDataImpl(ByteBuffer pageData) {
        super.loadPageDataImpl(pageData);
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
