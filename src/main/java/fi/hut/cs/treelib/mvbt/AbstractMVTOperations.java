package fi.hut.cs.treelib.mvbt;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.MVTreeOperations;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.AbstractMVTree;
import fi.hut.cs.treelib.common.AbstractMVTreePage;
import fi.hut.cs.treelib.common.AbstractTreeOperations;
import fi.hut.cs.treelib.common.DummyTransaction;
import fi.hut.cs.treelib.common.PagePath;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public abstract class AbstractMVTOperations<K extends Key<K>, V extends PageValue<?>, P extends AbstractMVTreePage<K, V, P>>
    extends AbstractTreeOperations<K, V, P> implements MVTreeOperations<K, V, P> {

    private static final Logger log = Logger.getLogger(AbstractMVTOperations.class);

    protected final AbstractMVTree<K, V, P> tree;

    protected AbstractMVTOperations(AbstractMVTree<K, V, P> tree) {
        super(tree);
        this.tree = tree;
    }

    @Override
    protected AbstractMVTree<K, V, P> getTree() {
        return tree;
    }

    /**
     * Inserts a key-value pair into this page, splitting pages along the path
     * if necessary.
     * 
     * @return the page where the value was inserted to.
     */
    @Override
    public boolean insert(PagePath<K, V, P> path, K key, V value, Transaction<K, V> tx) {
        P page = path.getCurrent();
        assert page.isLeafPage();

        if (!page.isFull()) {
            // Insert value into this page
            // Store key in a range of [key, key+1)@[currentVersion, INF)
            page.insert(key, value, tx);
            if (log.isDebugEnabled())
                log.debug(String.format("Leaf page %s was not full, added key %s",
                    page.getName(), key.toString()));
            return true;
        }

        // No more space here, need to split
        if (log.isDebugEnabled())
            log.debug(String.format("Page %s is full when inserting, need to split", page
                .getName()));

        split(path, key, null, true, tx);

        // After split, insert to the new page
        return insert(path, key, value, tx);
    }

    /**
     * Finds the leaf page whose key range contains the given key in the given
     * tree version. All pages in the resulting path are fixed once by this
     * method and must be released by the caller (use unfixPath(path)).
     * 
     * Fix path so that it is the path to the appropriate leaf page
     */
    @Override
    public void findPathToLeafPage(PageID rootPageID, K key, PagePath<K, V, P> path, int version,
        Owner owner) {
        assert path.isEmpty();

        P page = buffer.fixPage(rootPageID, factory, false, owner);
        path.attachRoot(page);

        while (!page.isLeafPage()) {
            assert page.getKeyRange().contains(key, version) : String.format(
                "Index page %s does not contain key %s@%d", page.getName(), key, version);

            PageValue<?> value = page.getEntry(key, new DummyTransaction<K, V>(version, version,
                owner));
            PageID childID = (PageID) value;
            assert childID != null : String.format(
                "Index page %s does not contain child page with key %s", page.getName(), key);

            P child = buffer.fixPage(childID, factory, false, owner);
            path.descend(child);
            page = child;
        }
    }

    @Override
    protected MVKeyRange<K> getKeyRange(K min, K max, P originatingPage, Transaction<K, V> tx) {
        return new MVKeyRange<K>(min, max, tx.getReadVersion());
    }

    /**
     * Backs up the saved path and finds the leaf page that contains the given
     * key, starting from the given saved path. The leaf page is supposed to
     * contain higher keys than the previous one.
     */
    protected void findNextLeafPage(PagePath<K, V, P> path, K key, int version, Owner owner) {
        // Sanity checks
        assert !path.isEmpty();
        P page = path.getCurrent();
        assert page.isLeafPage();
        assert page.getKeyRange().getMax().compareTo(key) <= 0;

        // Trace back up the tree
        path.ascend();
        buffer.unfix(page, owner);

        while (true) {
            page = path.getCurrent();
            if (!page.getKeyRange().contains(key, version)) {
                // Backtrack more
                path.ascend();
                buffer.unfix(page, owner);
                continue;
            }
            // Check if we have found the correct leaf page
            if (page.isLeafPage())
                return;

            // Find correct child
            PageID childID = (PageID) page.getEntry(key, new DummyTransaction<K, V>(version,
                version, owner));
            assert childID != null;
            P child = buffer.fixPage(childID, factory, false, owner);
            assert child != null;
            assert child.getKeyRange().contains(key, version);
            path.descend(child);
        }
    }

    /**
     * Scans through pages, looking for entries. Saved path is used to boost
     * the search.
     * 
     * @return true if all callbacks returned true, false if one of them
     * returned false to stop the search.
     */
    protected boolean getRange(PageID rootPageID, KeyRange<K> range,
        Callback<Pair<K, V>> callback, PagePath<K, V, P> path, Transaction<K, V> tx) {
        if (path.isEmpty()) {
            findPathToLeafPage(rootPageID, range.getMin(), path, tx.getReadVersion(), tx);
            assert !path.isEmpty();
        }

        K curKey = range.getMin();

        while (true) {
            P page = path.getCurrent();
            assert page != null;
            assert page.isLeafPage();
            assert page.getKeyRange().contains(curKey, tx.getReadVersion())
                || page.getKeyRange().getMax().equals(curKey) : page + " does not contain "
                + curKey + "@" + tx.getReadVersion();

            if (!page.processLeafEntries(tx.getReadVersion(), range, callback)) {
                // Stop search. Saved path left as it is, if search is
                // continued at some point.
                return false;
            }

            curKey = page.getKeyRange().getMax();
            if (curKey.compareTo(range.getMax()) >= 0) {
                // The range ends at this page; stop search
                return true;
            }
            if (curKey.equals(curKey.getMaxKey())) {
                // We are at the last page; stop search
                return true;
            }
            // Search to the leaf page that contains curKey
            findNextLeafPage(path, curKey, tx.getReadVersion(), tx);
        }
    }
}
