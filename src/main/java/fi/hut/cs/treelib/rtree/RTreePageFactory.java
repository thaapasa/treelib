package fi.hut.cs.treelib.rtree;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.storage.PageFactory;

public class RTreePageFactory<K extends Key<K>, V extends PageValue<?>> implements
    PageFactory<RTreePage<K, V>> {

    private final RTree<K, V> tree;

    public RTreePageFactory(RTree<K, V> tree) {
        this.tree = tree;
    }

    @Override
    public RTreePage<K, V> createEmptyPage(PageID pageID) {
        RTreePage<K, V> page = new RTreePage<K, V>(tree, pageID);
        return page;
    }

    @Override
    public int getTypeIdentifier() {
        return RTreePage.PAGE_IDENTIFIER;
    }

}
