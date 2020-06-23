package fi.hut.cs.treelib.common;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;

public abstract class AbstractBTreeOperations<K extends Key<K>, V extends PageValue<?>, P extends AbstractTreePage<K, V, P>>
    extends AbstractTreeOperations<K, V, P> {

    private static final Logger log = Logger.getLogger(AbstractBTreeOperations.class);
    private final AbstractTree<K, V, P> tree;

    protected AbstractBTreeOperations(AbstractTree<K, V, P> tree) {
        super(tree);
        this.tree = tree;
    }

    /**
     * Checks the page for underflow. This method will also correct the
     * situation if an underflow has occured.
     * 
     * @param page the page to check
     * @return true if an underflow occurred (and was corrected)
     */
    public P checkUnderflow(PagePath<K, V, P> path, Transaction<K, V> tx) {
        P page = path.getCurrent();

        if (log.isDebugEnabled())
            log.debug(String.format("Checking page %s for underflow", page.getName()));
        int entries = page.getEntryCount();
        if (entries >= page.getMinEntries())
            // No action needed
            return page;

        if (!page.isRoot(path)) {
            // In non-root page, at this point, the entry count must be
            // minEntries - 1 (since it can never be below minEntries
            // except when rebalancing).
            assert entries == page.getMinEntries() - 1 : String.format(
                "Wrong number of entries in page %s " + "(was %d, should be %d)", page.getName(),
                entries, page.getMinEntries());

            // Merge page with a sibling page
            merge(path, tx, page.getKeyRange().getMin());
            return path.getCurrent();
        } else {
            // Min entries does not apply to root
            if (entries == 0) {
                path.ascend();
                assert path.isEmpty();
                // Page is removed from path
                buffer.unfix(page, tx);

                // Last page has been deleted from the tree, so delete the
                // root page from the database
                tree.deleteRoot(page, tx);
            } else {
                return checkRootUnderflow(path, tx);
            }
        }
        return page;
    }

    protected abstract void merge(PagePath<K, V, P> path, Transaction<K, V> tx, K key);

    protected abstract P checkRootUnderflow(PagePath<K, V, P> path, Transaction<K, V> tx);
}
