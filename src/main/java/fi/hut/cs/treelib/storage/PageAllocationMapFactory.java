package fi.hut.cs.treelib.storage;

import fi.hut.cs.treelib.PageID;

public class PageAllocationMapFactory implements PageFactory<PageAllocationMap> {

    private final int pageSize;

    public PageAllocationMapFactory(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public PageAllocationMap createEmptyPage(PageID pageID) {
        return new PageAllocationMap(pageID, pageSize);
    }

    @Override
    public int getTypeIdentifier() {
        return PageAllocationMap.PAGE_IDENTIFIER;
    }

}
