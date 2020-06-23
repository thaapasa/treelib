package fi.hut.cs.treelib.mvbt;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.storage.AbstractInfoPage;

public class MVBTInfoPage<K extends Key<K>> extends AbstractInfoPage<MVBTInfoPage<K>> {

    public static final int PAGE_IDENTIFIER = 0x3ed71f0;

    private int activeVersion = 0;
    private int committedVersion = 0;
    /** Never null, but initialized to a default value. */
    private MVKeyRange<K> keyRange;

    protected MVBTInfoPage(int pageSize, MVKeyRange<K> defaultRange) {
        super(pageSize, PAGE_IDENTIFIER);
        this.keyRange = defaultRange;
    }

    public MVBTInfoPage(PageID pageID, int pageSize, MVKeyRange<K> defaultRange) {
        super(pageID, pageSize, PAGE_IDENTIFIER);
        this.keyRange = defaultRange;
    }

    @Override
    public MVBTInfoPage<K> createEmptyPage(PageID pageID) {
        return new MVBTInfoPage<K>(pageID, getPageSize(), keyRange);
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

    public void setActiveVersion(int version) {
        if (this.activeVersion != version) {
            this.activeVersion = version;
            setDirty(true);
        }
    }

    public int getActiveVersion() {
        return activeVersion;
    }

    public int getCommittedVersion() {
        return committedVersion;
    }

    public void setCommittedVersion(int committedVersion) {
        if (this.committedVersion != committedVersion) {
            this.committedVersion = committedVersion;
            setDirty(true);
        }
    }

    @Override
    protected boolean loadPageDataImpl(ByteBuffer pageData) {
        boolean dataOk = super.loadPageDataImpl(pageData);
        if (!dataOk)
            return false;

        activeVersion = pageData.getInt();
        committedVersion = pageData.getInt();
        keyRange = keyRange.readFromBytes(pageData);
        return true;
    }

    @Override
    protected void savePageDataImpl(ByteBuffer pageData) {
        super.savePageDataImpl(pageData);
        pageData.putInt(activeVersion);
        pageData.putInt(committedVersion);
        keyRange.writeToBytes(pageData);
    }

}
