package fi.hut.cs.treelib.storage;

import fi.hut.cs.treelib.PageID;

public class TestPageFactory implements PageFactory<TestPage> {

    private final int pageSize;

    public TestPageFactory(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public TestPage createEmptyPage(PageID pageID) {
        TestPage page = new TestPage(pageID, pageSize, 1);
        return page;
    }

    @Override
    public int getTypeIdentifier() {
        return TestPage.PAGE_IDENTIFIER;
    }

}
