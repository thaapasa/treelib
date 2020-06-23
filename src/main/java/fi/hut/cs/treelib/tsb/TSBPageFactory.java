package fi.hut.cs.treelib.tsb;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.storage.PageFactory;

public class TSBPageFactory<K extends Key<K>, V extends PageValue<?>> implements
    PageFactory<TSBPage<K, V>> {

    private final TSBTree<K, V> tree;
    private final int pageSize;

    protected TSBPageFactory(TSBTree<K, V> tree, int pageSize) {
        this.tree = tree;
        this.pageSize = pageSize;
    }

    @Override
    public TSBPage<K, V> createEmptyPage(PageID pageID) {
        return new TSBPage<K, V>(tree, pageID, pageSize);
    }

    @Override
    public int getTypeIdentifier() {
        return TSBPage.PAGE_IDENTIFIER;
    }

}
