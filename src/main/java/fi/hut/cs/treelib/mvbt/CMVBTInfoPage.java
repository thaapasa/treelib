package fi.hut.cs.treelib.mvbt;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.storage.AbstractInfoPage;

public class CMVBTInfoPage<K extends Key<K>> extends AbstractInfoPage<CMVBTInfoPage<K>> {

    public static final int PAGE_IDENTIFIER = 0xc3ed71f0;

    private int maxCommittedVersion = 0;

    /** Never null, but initialized to a default value. */
    private MVKeyRange<K> keyRange;

    protected CMVBTInfoPage(int pageSize, MVKeyRange<K> defaultRange) {
        super(pageSize, PAGE_IDENTIFIER);
        this.keyRange = defaultRange;
    }

    public CMVBTInfoPage(PageID pageID, int pageSize, MVKeyRange<K> defaultRange) {
        super(pageID, pageSize, PAGE_IDENTIFIER);
        this.keyRange = defaultRange;
    }

    @Override
    public CMVBTInfoPage<K> createEmptyPage(PageID pageID) {
        return new CMVBTInfoPage<K>(pageID, getPageSize(), keyRange);
    }

    public MVKeyRange<K> getKeyRange() {
        return keyRange;
    }

    public void setKeyRange(MVKeyRange<K> keyRange) {
        if (!this.keyRange.equals(keyRange)) {
            setDirty(true);
        }
        this.keyRange = keyRange;
    }

    public void setMaxCommittedVersion(int version) {
        if (maxCommittedVersion != version) {
            this.maxCommittedVersion = version;
            setDirty(true);
        }
    }

    public int getMaxCommittedVersion() {
        return maxCommittedVersion;
    }

    @Override
    protected boolean loadPageDataImpl(ByteBuffer pageData) {
        boolean dataOk = super.loadPageDataImpl(pageData);
        if (!dataOk)
            return false;

        keyRange = keyRange.readFromBytes(pageData);
        maxCommittedVersion = pageData.getInt();
        return true;
    }

    @Override
    protected void savePageDataImpl(ByteBuffer pageData) {
        super.savePageDataImpl(pageData);
        keyRange.writeToBytes(pageData);
        pageData.putInt(maxCommittedVersion);
    }

}
