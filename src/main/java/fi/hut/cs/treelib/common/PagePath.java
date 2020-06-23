package fi.hut.cs.treelib.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Pair;

public class PagePath<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> implements
    Iterable<Pair<P, P>> {

    private static final Logger log = Logger.getLogger(PagePath.class);

    private final boolean maintainFullPath;
    private List<P> path = new ArrayList<P>();

    /**
     * Used to mark that MBRs in the path must be extended (for MDTrees, such
     * as an R-tree)
     */
    private boolean extendPageKeys = false;

    public PagePath(boolean maintainFullPath) {
        this.maintainFullPath = maintainFullPath;
    }

    public PagePath(P root, boolean maintainFullPath) {
        this.maintainFullPath = maintainFullPath;
        attachRoot(root);
    }

    public boolean isExtendPageKeys() {
        return extendPageKeys;
    }

    public void setExtendPageKeys(boolean extendPageKeys) {
        this.extendPageKeys = extendPageKeys;
    }

    public boolean isMaintainFullPath() {
        return maintainFullPath;
    }

    public P getRoot() {
        return path.size() > 0 ? path.get(0) : null;
    }

    public int size() {
        return path.size();
    }

    public void descend(P child) {
        if (path.size() == 0) {
            attachRoot(child);
            return;
        }
        if (getCurrent() == child) {
            return;
        }
        log.debug("Descending to " + child);
        P parent = getCurrent();
        assert parent.containsChild(child.getPageID()) : "Cannot descend from " + parent + " to "
            + child;
        path.add(child);
    }

    @SuppressWarnings("unchecked")
    public void descend(K key, Owner owner) {
        if (path.size() == 0) {
            throw new IllegalStateException("No pages in path, cannot automatically descend");
        }
        P page = getCurrent();
        assert page != null;
        if (page.isLeafPage()) {
            log.debug(String.format("Page %s is leaf, cannot descend", page.getName()));
            return;
        }
        // Adds a fixed page to the path
        P child = (P) page.getChild(key, owner);
        log.debug("Descending to " + child);
        path.add(child);
    }

    public P ascend() {
        if (path.size() == 0) {
            throw new IllegalStateException("No pages left in path, cannot ascend");
        }
        P child = path.remove(path.size() - 1);
        log.debug("Ascending from " + child);
        return getCurrent();
    }

    public P getParent() {
        return path.size() > 1 ? path.get(path.size() - 2) : null;
    }

    public P getCurrent() {
        return path.size() > 0 ? path.get(path.size() - 1) : null;
    }

    public boolean isEmpty() {
        return path.isEmpty();
    }

    public void attachRoot(P root) {
        log.debug("Starting path from " + root);
        if (!path.isEmpty()) {
            throw new IllegalStateException("Path not empty when attaching root");
        }
        path.add(root);
    }

    @Override
    public String toString() {
        return "PagePath <" + CollectionUtils.join(path, ", ") + ">";
    }

    public Iterator<Pair<P, P>> iterator() {
        return topDownIterator();
    }

    /**
     * @returns an iterator of page-parent pairs of the form Pair<Page p,
     * Parent-of(p)>. Note that for the root, the parent is null.
     */
    public Iterator<Pair<P, P>> topDownIterator() {
        return new Iterator<Pair<P, P>>() {
            private int pos = 0;

            @Override
            public boolean hasNext() {
                return pos < path.size();
            }

            @Override
            public Pair<P, P> next() {
                if (!hasNext())
                    throw new NoSuchElementException("No more elements");
                P page = path.get(pos);
                P parent = pos > 0 ? path.get(pos - 1) : null;
                pos++;
                return new Pair<P, P>(page, parent);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove() not supported");
            }
        };
    }

    /**
     * @returns an iterator of page-parent pairs of the form Pair<Page p,
     * Parent-of(p)>. Note that for the root, the parent is null.
     */
    public Iterator<Pair<P, P>> bottomUpIterator() {
        return new Iterator<Pair<P, P>>() {
            private int pos = path.size() - 1;

            @Override
            public boolean hasNext() {
                return pos >= 0;
            }

            @Override
            public Pair<P, P> next() {
                if (!hasNext())
                    throw new NoSuchElementException("No more elements");
                P page = path.get(pos);
                P parent = pos > 0 ? path.get(pos - 1) : null;
                pos--;
                return new Pair<P, P>(page, parent);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove() not supported");
            }
        };
    }
}
