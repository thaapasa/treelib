package fi.hut.cs.treelib.mvbt;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.storage.PageFactory;

public class MVBTPageFactory<K extends Key<K>, V extends PageValue<?>> implements
    PageFactory<MVBTPage<K, V>> {

    private final MVBTree<K, V> tree;

    public MVBTPageFactory(MVBTree<K, V> tree) {
        this.tree = tree;
    }

    @Override
    public MVBTPage<K, V> createEmptyPage(PageID pageID) {
        assert tree != null;
        MVBTPage<K, V> node = new MVBTPage<K, V>(tree, pageID);
        return node;
    }

    @Override
    public int getTypeIdentifier() {
        return MVBTPage.PAGE_IDENTIFIER;
    }

}
