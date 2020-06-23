package fi.hut.cs.treelib.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.PagePath;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class TestPage extends AbstractPage implements StoredPage, Page<IntegerKey, IntegerValue> {

    public static final int PAGE_IDENTIFIER = 0x7e577e57;

    private int singleEntrySize;

    public int data = 0;

    public TestPage(PageID pageID, int pageSize, int entrySize) {
        super(pageID, pageSize);
        this.singleEntrySize = entrySize;
    }

    @Override
    public void formatNewPage(ByteBuffer pageData) {
        super.formatNewPage(pageData);
        this.data = 0;
    }

    @Override
    protected boolean loadPageDataImpl(ByteBuffer pageData) {
        int pageType = pageData.getInt();
        if (pageType != getTypeIdentifier())
            return false;

        data = pageData.getInt();
        return true;
    }

    @Override
    public void savePageDataImpl(ByteBuffer pageData) {
        pageData.putInt(getTypeIdentifier());
        pageData.putInt(data);
    }

    @Override
    public int getTypeIdentifier() {
        return PAGE_IDENTIFIER;
    }

    @Override
    public boolean contains(IntegerKey key) {
        return false;
    }

    @Override
    public boolean containsChild(PageID childID) {
        return false;
    }

    @Override
    public PageID findChildPointer(IntegerKey key) {
        return null;
    }

    @Override
    public void format(int height) {
    }

    @Override
    public Page<IntegerKey, IntegerValue> getChild(IntegerKey key, Owner owner) {
        return null;
    }

    @Override
    public List<PageValue<?>> getEntries() {
        return new ArrayList<PageValue<?>>();
    }

    @Override
    public PageValue<?> getEntry(IntegerKey key) {
        return null;
    }

    @Override
    public int getEntryCount() {
        return 0;
    }

    @Override
    public double getFillRatio() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 1;
    }

    @Override
    public KeyRange<IntegerKey> getKeyRange() {
        return null;
    }

    @Override
    public String getName() {
        return "TestPage";
    }

    @Override
    public int getPageEntryCapacity() {
        return getPageSize() / singleEntrySize;
    }

    @Override
    public String getShortName() {
        return "testpage";
    }

    @Override
    public int getSingleEntrySize() {
        return singleEntrySize;
    }

    @Override
    public boolean isFull() {
        return false;
    }

    @Override
    public boolean isLeafPage() {
        return true;
    }

    @Override
    public boolean isRoot(
        PagePath<IntegerKey, IntegerValue, ? extends Page<IntegerKey, IntegerValue>> path) {
        return false;
    }

    @Override
    public boolean processEntries(Callback<Pair<KeyRange<IntegerKey>, PageValue<?>>> callback) {
        return false;
    }
}
