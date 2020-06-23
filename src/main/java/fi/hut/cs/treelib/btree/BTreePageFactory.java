package fi.hut.cs.treelib.btree;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.storage.PageFactory;

public class BTreePageFactory<K extends Key<K>, V extends PageValue<?>> implements
    PageFactory<BTreePage<K, V>> {

    private final BTree<K, V> tree;
    private int pageSize;

    public BTreePageFactory(BTree<K, V> tree, int pageSize) {
        this.tree = tree;
        this.pageSize = pageSize;
    }

    @Override
    public BTreePage<K, V> createEmptyPage(PageID pageID) {
        BTreePage<K, V> node = new BTreePage<K, V>(tree, pageID, pageSize);
        return node;
    }

    @Override
    public int getTypeIdentifier() {
        return BTreePage.PAGE_IDENTIFIER;
    }

}
