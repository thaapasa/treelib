package fi.hut.cs.treelib.tsb;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.storage.AbstractInfoPage;

public class TSBInfoPage<K extends Key<K>> extends AbstractInfoPage<TSBInfoPage<K>> {

    public static final int PAGE_IDENTIFIER = 0x75b71f0;

    private int committedVersion = 0;
    private int lastOwnerID = 0;
    private MVKeyRange<K> keyRange;

    protected TSBInfoPage(int pageSize, MVKeyRange<K> defaultKeyRange) {
        super(pageSize, PAGE_IDENTIFIER);
        this.keyRange = defaultKeyRange;
    }

    public TSBInfoPage(PageID pageID, int pageSize, MVKeyRange<K> defaultKeyRange) {
        super(pageID, pageSize, PAGE_IDENTIFIER);
        this.keyRange = defaultKeyRange;
    }

    @Override
    public TSBInfoPage<K> createEmptyPage(PageID pageID) {
        return new TSBInfoPage<K>(pageID, getPageSize(), keyRange);
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

    public void setCommittedVersion(int version) {
        if (this.committedVersion != version) {
            this.committedVersion = version;
            setDirty(true);
        }
    }

    public int getCommittedVersion() {
        return committedVersion;
    }

    public int getLastOwnerID() {
        return lastOwnerID;
    }

    public void setLastOwnerID(int lastOwnerID) {
        if (this.lastOwnerID != lastOwnerID) {
            this.lastOwnerID = lastOwnerID;
            setDirty(true);
        }
    }

    @Override
    protected boolean loadPageDataImpl(ByteBuffer pageData) {
        boolean dataOk = super.loadPageDataImpl(pageData);
        if (!dataOk)
            return false;

        committedVersion = pageData.getInt();
        lastOwnerID = pageData.getInt();
        keyRange = keyRange.readFromBytes(pageData);
        return true;
    }

    @Override
    protected void savePageDataImpl(ByteBuffer pageData) {
        super.savePageDataImpl(pageData);

        pageData.putInt(committedVersion);
        pageData.putInt(lastOwnerID);
        keyRange.writeToBytes(pageData);
    }

}
