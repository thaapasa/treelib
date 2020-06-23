package fi.hut.cs.treelib.common;

import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

/**
 * Contains basic functionality required for all sorts of trees. This includes
 * references to page buffer and page factory, statistics logger, key and
 * value prototypes, etc.
 * 
 * @author thaapasa
 * 
 * @param <K> tree key type
 * @param <V> tree value type
 * @param <P> page type
 */
public abstract class AbstractMDTree<K extends Key<K>, V extends PageValue<?>, L extends Key<L>, P extends AbstractMDPage<K, V, L, P>>
    extends AbstractTree<MBR<K>, V, P> implements MDTree<K, V, P> {

    /**
     * Not null, but will be prototype if the extents have not been
     * initialized.
     */
    private MBR<K> extents;

    protected AbstractMDTree(String identifier, String name, PageID infoPageID,
        DatabaseConfiguration<MBR<K>, V> dbConfig) {
        super(identifier, name, infoPageID, dbConfig);
        extents = dbConfig.getKeyPrototype();
        assert extents.isPrototype();
    }

    @Override
    public boolean insert(MBR<K> key, V value, PagePath<MBR<K>, V, P> savedPath,
        Transaction<MBR<K>, V> tx) {
        assert !key.isPrototype();
        // When inserting, we need to travel by the search key, so the
        // standard findPathToLeafNode(key) works fine.
        savedPath.setExtendPageKeys(true);
        if (super.insert(key, value, savedPath, tx)) {
            // Update tree extents
            if (extents.isPrototype() || !extents.contains(key)) {
                extents = !extents.isPrototype() ? extents.extend(key) : key;
                updateInfoPage(tx);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return the new page, fixed to buffer
     */
    @Override
    public P createSiblingPage(P node, Owner owner) {
        P sibling = pageBuffer.createPage(pageFactory, owner);
        sibling.format(node.getHeight());
        sibling.setPageMBR(node.getPageMBR());
        return sibling;
    }

    protected void setExtents(MBR<K> extents) {
        this.extents = extents != null ? extents : getKeyPrototype();
    }

    protected void checkPageLimits(P prevPage, P newPage, K key) {
        assert prevPage != null;
        prevPage.recalculateMBR();
    }

    @Override
    protected void extendRangeBulkLoad(MBR<K> key) {
        super.extendKeyRange(key);
        extents = extents != null && !extents.isPrototype() ? extents.extend(key) : key;
    }

    @Override
    public MBR<K> getExtents() {
        return extents;
    }

    @Override
    public void traversePages(final Predicate<KeyRange<MBR<K>>> predicate,
        final Callback<Page<MBR<K>, V>> operation, Owner owner) {
        throw new UnsupportedOperationException("Use traverseMDPages() instead");
    }

    public boolean traverseMDPages(final Predicate<MBR<K>> predicate,
        final Callback<Page<MBR<K>, V>> operation, Owner owner) {
        P root = getRoot(owner);
        if (root == null)
            return true;

        // Unlog a single fix from the stats (getRoot() takes one fix and
        // traverseMDPages() takes another for the root page)
        stats.unlog(Operation.OP_BUFFER_FIX);

        boolean res = root.traverseMDPages(predicate, operation, owner);
        pageBuffer.unfix(root, owner);
        return res;
    }

    public boolean traverseMDEntries(final Predicate<MBR<K>> predicate,
        final Callback<Pair<MBR<K>, PageValue<?>>> operation, Owner owner) {
        return traverseMDPages(predicate, new Callback<Page<MBR<K>, V>>() {
            @Override
            @SuppressWarnings("unchecked")
            public boolean callback(Page<MBR<K>, V> page) {
                AbstractMDPage<K, V, ?, ?> mPage = (AbstractMDPage<K, V, ?, ?>) page;
                return mPage.processMBREntries(new Callback<Pair<MBR<K>, PageValue<?>>>() {
                    @Override
                    public boolean callback(Pair<MBR<K>, PageValue<?>> entry) {
                        if (predicate.matches(entry.getFirst(), 1)) {
                            return operation.callback(entry);
                        } else
                            return true;
                    }
                });
            }
        }, owner);
    }

    @Override
    public boolean getRange(KeyRange<MBR<K>> range, Callback<Pair<MBR<K>, V>> callback,
        Transaction<MBR<K>, V> tx) {
        throw new UnsupportedOperationException("JTree does not support range queries");
    }

    @Override
    public boolean isMultiVersion() {
        return false;
    }

}
