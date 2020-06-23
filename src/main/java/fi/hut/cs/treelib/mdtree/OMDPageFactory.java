package fi.hut.cs.treelib.mdtree;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.common.AbstractMDTree;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.tuska.util.Converter;

/**
 * Page factory for ordered multidimensional tree pages. Used directly by
 * J-tree and Hilbert R-tree.
 * 
 * @author thaapasa
 * 
 * @param <K>
 * @param <V>
 * @param <L>
 */
public class OMDPageFactory<K extends Key<K>, V extends PageValue<?>, L extends Key<L>>
    implements PageFactory<OMDPage<K, V, L>> {

    private final AbstractMDTree<K, V, L, OMDPage<K, V, L>> tree;
    private final Converter<MBR<K>, L> searchKeyCreator;

    public OMDPageFactory(AbstractMDTree<K, V, L, OMDPage<K, V, L>> tree,
        Converter<MBR<K>, L> searchKeyCreator) {
        this.tree = tree;
        this.searchKeyCreator = searchKeyCreator;
    }

    @Override
    public OMDPage<K, V, L> createEmptyPage(PageID pageID) {
        OMDPage<K, V, L> page = new OMDPage<K, V, L>(tree, pageID, searchKeyCreator);
        return page;
    }

    @Override
    public int getTypeIdentifier() {
        return OMDPage.PAGE_IDENTIFIER;
    }

}
