package fi.hut.cs.treelib.rtree;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDPage;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.common.AbstractMDPage;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.gui.TreeDrawStyle;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.tuska.util.Callback;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Converter;
import fi.tuska.util.IteratorWrapper;
import fi.tuska.util.Pair;
import fi.tuska.util.SortedList;
import fi.tuska.util.iterator.Iterables;

public class RTreePage<K extends Key<K>, V extends PageValue<?>> extends
    AbstractMDPage<K, V, MBR<K>, RTreePage<K, V>> implements MDPage<K, V, MBR<K>>, Component {

    public static final int PAGE_IDENTIFIER = 0xa87ba6e;

    protected final RTree<K, V> tree;

    /**
     * R-trees: for index pages, the keys are the enclosing MBR of the child
     * page. For leaf pages, the keys are the MBR of data entries.
     */
    private SortedList<MBR<K>, PageValue<?>> contents = new SortedList<MBR<K>, PageValue<?>>();

    public RTreePage(RTree<K, V> tree, PageID id) {
        super(id, tree);
        this.tree = tree;
    }

    @Override
    protected void clearContents() {
        contents.clear();
    }

    @Override
    public int getEntryCount() {
        return contents.size();
    }

    @Override
    public TextLine[] getPageData(int version, TreeDrawStyle scheme) {
        String[] keys = new String[contents.size()];
        int c = 0;
        for (Pair<MBR<K>, PageValue<?>> entry : contents) {
            if (isLeafPage()) {
                keys[c++] = entry.getFirst().toString() + ": " + entry.getSecond().toString();
            } else {
                keys[c++] = entry.getFirst().toString() + ": "
                    + ((PageID) entry.getSecond()).intValue();
            }
        }
        return TextLine.toTextLines(keys);
    }

    /** Sets the page dirty, and extends the page's own MBR. */
    @Override
    public void putContents(KeyRange<MBR<K>> key, PageValue<?> value) {
        putContents(key.getMin(), value);
    }

    /** Sets the page dirty, and extends the page's own MBR. */
    @Override
    public void putContents(MBR<K> mbr, PageValue<?> value) {
        // No direct links to other pages
        assert !(value instanceof Page<?, ?>);
        assert !isFull() : String.format("Page %s is full when storing contents", getName());

        contents.add(mbr, value);
        extendPageMBR(mbr);
        setDirty(true);
    }

    /** Sets the page dirty, if something was removed */
    public PageValue<?> removeContents(MBR<K> mbr) {
        PageValue<?> value = contents.removeFirst(mbr);
        if (value == null)
            return null;
        setDirty(true);
        return value;
    }

    public boolean removeContents(MBR<K> mbr, PageID childID) {
        assert !isLeafPage();
        boolean res = contents.remove(mbr, childID);
        return res;
    }

    public void recalculateAndUpdateMBR(RTreePage<K, V> parent) {
        MBR<K> oldMBR = getPageMBR();
        recalculateMBR();
        if (parent != null) {
            if (!oldMBR.equals(getPageMBR())) {
                // Update pointer at parent
                parent.removeContents(oldMBR, getPageID());
                parent.putContents(getPageMBR(), getPageID());
            }
        }
    }

    /**
     * Called when moving pages from one child to a sibling.
     */
    protected void updateKeyRange(RTreePage<K, V> child, MBR<K> newRange) {
        assert !isLeafPage();
        PageID childID = child.getPageID();

        boolean found = false;
        for (Iterator<Pair<MBR<K>, PageValue<?>>> i = contents.iterator(); i.hasNext();) {
            Pair<MBR<K>, PageValue<?>> entry = i.next();
            if (entry.getSecond().equals(childID)) {
                i.remove();
                found = true;
                break;
            }
        }
        assert found : child + " not found at " + this;

        putContents(newRange, child.getPageID());
        setDirty(true);

        child.setPageMBR(newRange);
    }

    @Override
    public int getSingleEntrySize() {
        int size = 0;
        // Key range size
        size += dbConfig.getKeyPrototype().getByteDataSize();

        // Data storage size
        if (isLeafPage())
            size += dbConfig.getValuePrototype().getByteDataSize();
        else
            size += PageID.PROTOTYPE.getByteDataSize();
        return size;
    }

    @Override
    protected MBR<K> loadSingleEntry(ByteBuffer data, int index, int entryCount) {
        assert data.remaining() >= getSingleEntrySize() : data.remaining() + " < "
            + getSingleEntrySize();
        // Read page MBR
        MBR<K> mbr = dbConfig.getKeyPrototype().readFromBytes(data);

        // Read stored value
        PageValue<?> value = null;
        if (isLeafPage())
            value = dbConfig.getValuePrototype().readFromBytes(data);
        else
            value = PageID.PROTOTYPE.readFromBytes(data);

        assert value != null;
        putContents(mbr, value);
        return mbr;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object saveSingleEntry(ByteBuffer data, int index, Object position) {
        assert data.remaining() >= getSingleEntrySize() : data.remaining() + " < "
            + getSingleEntrySize();

        Iterator<Pair<MBR<K>, PageValue<?>>> iterator = (Iterator<Pair<MBR<K>, PageValue<?>>>) position;
        if (iterator == null) {
            iterator = contents.iterator();
        }
        assert iterator.hasNext();

        Pair<MBR<K>, PageValue<?>> entry = iterator.next();
        assert entry != null;

        MBR<K> mbr = entry.getFirst();
        // Write entry MBR
        mbr.writeToBytes(data);

        // Write stored contents
        PageValue<?> value = entry.getSecond();
        assert value != null;
        value.writeToBytes(data);

        return iterator;
    }

    /**
     * Only used for visualization. Does not follow proper page fixing
     * policies (collects unfixed pages).
     */
    protected void collectPagesAtHeight(int height, Collection<VisualizablePage<MBR<K>, V>> result) {
        if (getHeight() == height) {
            result.add(this);
        } else {
            if (getHeight() > height) {
                for (Pair<MBR<K>, PageValue<?>> entry : this.contents) {
                    PageID childID = (PageID) entry.getSecond();
                    RTreePage<K, V> child = dbConfig.getPageBuffer().fixPage(childID, factory,
                        false, tree.internalOwner);
                    child.collectPagesAtHeight(height, result);
                    dbConfig.getPageBuffer().unfix(child, tree.internalOwner);
                }
            }
        }
    }

    /**
     * Convenience method from VisualizablePage. Does not follow proper page
     * fixing policies. Must only be used by the visualization program.
     */
    @Override
    public List<VisualizablePage<MBR<K>, V>> getChildren() {
        if (isLeafPage()) {
            // Leaves have no children
            return new ArrayList<VisualizablePage<MBR<K>, V>>();
        }
        List<VisualizablePage<MBR<K>, V>> res = new ArrayList<VisualizablePage<MBR<K>, V>>();

        for (PageValue<?> child : Iterables.get(contents.contentIterator())) {
            PageID router = (PageID) child;
            RTreePage<K, V> bChild = dbConfig.getPageBuffer().fixPage(router, factory, false,
                tree.internalOwner);
            res.add(bChild);
            // The pages are released before the calling program uses them, so
            // this only works for the visualization program.
            dbConfig.getPageBuffer().unfix(bChild, tree.internalOwner);
        }
        return res;
    }

    /**
     * For insert: finds a router that needs the least enlargement; and
     * enlarges it to contain the given MBR.
     * 
     * @return the new router entry
     */
    public Pair<MBR<K>, PageID> findEntryAndEnlarge(MBR<K> mbr) {
        assert !isLeafPage();

        if (contents.isEmpty())
            return null;

        PriorityQueue<Pair<Float, Pair<MBR<K>, PageValue<?>>>> queue = new PriorityQueue<Pair<Float, Pair<MBR<K>, PageValue<?>>>>();

        for (Pair<MBR<K>, PageValue<?>> router : contents) {
            MBR<K> rMBR = router.getFirst();
            float mbrVal = rMBR.countEnlargement(mbr).toFloat();
            queue.add(new Pair<Float, Pair<MBR<K>, PageValue<?>>>(mbrVal, router));
        }

        Pair<Float, Pair<MBR<K>, PageValue<?>>> val = queue.remove();
        assert val != null;

        MBR<K> rMBR = val.getSecond().getFirst();
        PageID pageID = (PageID) val.getSecond().getSecond();
        // Check if the selected router MBR needs enlargement
        if (!rMBR.contains(mbr)) {
            // Enlarge the router MBR
            contents.remove(rMBR, pageID);
            rMBR = rMBR.extend(mbr);
            // putContents will set the page dirty
            putContents(rMBR, pageID);
        }
        return new Pair<MBR<K>, PageID>(rMBR, pageID);
    }

    @Override
    public PageValue<?> getEntry(MBR<K> key) {
        for (Pair<MBR<K>, PageValue<?>> entry : contents) {
            MBR<K> range = entry.getFirst();
            if ((isLeafPage() && range.equals(key)) || (!isLeafPage() && range.contains(key)))
                return entry.getSecond();
        }
        return null;
    }

    @Override
    public boolean contains(MBR<K> key) {
        if (isLeafPage()) {
            return contents.contains(key);
        } else {
            return getPageMBR().contains(key);
        }
    }

    @Override
    public boolean isRoot(PagePath<MBR<K>, V, ? extends Page<MBR<K>, V>> path) {
        return isRoot();
    }

    public Iterator<Pair<MBR<K>, PageValue<?>>> contentIterator() {
        return contents.iterator();
    }

    @Override
    public String getName() {
        String name = String.format("(%s, %s)", getPageMBR(), getShortName());
        return name;
    }

    @Override
    public List<MBR<K>> getKeys() {
        List<MBR<K>> keys = new ArrayList<MBR<K>>();
        // Go through all entries
        for (Pair<MBR<K>, PageValue<?>> entry : contents) {
            MBR<K> key = entry.getFirst();
            // Add duplicate keys if there are duplicates in the page
            keys.add(key);
        }
        return keys;
    }

    /**
     * @return the child page that contains the given key, fixed to the page
     * buffer.
     */
    @Override
    public RTreePage<K, V> getChild(MBR<K> key, Owner owner) {
        return super.getChild(key, owner);
    }

    @Override
    public void printDebugInfo() {
        super.printDebugInfo();
        System.out.println("MBR: " + getPageMBR());
        for (Pair<MBR<K>, PageValue<?>> entry : contents) {
            System.out.println(entry.getFirst() + ": " + entry.getSecond());
        }
    }

    @Override
    public KeyRange<MBR<K>> getKeyRange() {
        return KeyRangeImpl.getKeyRange(getPageMBR());
    }

    @Override
    public void setKeyRange(KeyRange<MBR<K>> keyRange) {
        // Called from format()
        // Just skip it
    }

    /**
     * Moves all entries with the given key from this page to the given page
     * 
     * @return the amount of entries moved
     */
    protected int moveAll(MBR<K> mbr, RTreePage<K, V> toPage) {
        int c = 0;
        while (contents.contains(mbr)) {
            PageValue<?> val = contents.removeFirst(mbr);
            // putContents sets the other page dirty
            toPage.putContents(mbr, val);
            c++;
        }
        if (c > 0) {
            setDirty(true);
        }
        return c;
    }

    @Override
    protected boolean processMBREntries(Callback<Pair<MBR<K>, PageValue<?>>> callback) {
        for (Pair<MBR<K>, PageValue<?>> entry : contents) {
            boolean doCont = callback.callback(new Pair<MBR<K>, PageValue<?>>(entry.getFirst(),
                entry.getSecond()));
            if (!doCont)
                return false;
        }
        return true;
    }

    @Override
    public PageID findChildPointer(MBR<K> key) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public List<PageValue<?>> getEntries() {
        return CollectionUtils.getPairSecondList(contents.getAll());
    }

    public Iterator<MBR<K>> getUniqueMBRs() {
        return contents.keyIterator();
    }

    @Override
    public Iterator<Pair<MBR<K>, MBR<K>>> getUniqueKeys() {
        return new IteratorWrapper<MBR<K>, Pair<MBR<K>, MBR<K>>>(contents.keyIterator(),
            new Converter<MBR<K>, Pair<MBR<K>, MBR<K>>>() {
                @Override
                public Pair<MBR<K>, MBR<K>> convert(MBR<K> mbr) {
                    return new Pair<MBR<K>, MBR<K>>(mbr, mbr);
                }
            });
    }

    @Override
    public RTreePage<K, V> getUniqueChild(MBR<K> key, MBR<K> mbr, Owner owner) {
        assert !isLeafPage();
        // Note: This is not actually unique for R-trees...
        PageID pageID = (PageID) contents.getFirst(mbr);
        return pageID != null ? dbConfig.getPageBuffer().fixPage(pageID, factory, false, owner)
            : null;
    }

    @Override
    public void checkConsistency(Object... params) {
        assert getPageMBR() != null;
        // Check that page MBR is correct (same as the union of the router
        // MBRs)
        MBR<K> calcMBR = null;
        for (Pair<MBR<K>, PageValue<?>> entry : contents) {
            MBR<K> childMBR = entry.getFirst();
            calcMBR = calcMBR != null ? calcMBR.extend(childMBR) : childMBR;
        }
        assert calcMBR != null;
        assert calcMBR.equals(getPageMBR()) : calcMBR + " != " + getPageMBR() + " at " + this;

        // Check that router MBRs match child page MBRs, and router search
        // keys match child page search keys, recurse down to child pages
        for (Pair<MBR<K>, PageValue<?>> entry : contents) {
            MBR<K> cMBR = entry.getFirst();
            assert getPageMBR().contains(cMBR);
            if (!isLeafPage()) {
                PageID cID = (PageID) entry.getSecond();
                assert cID != null;
                assert cID.isValid();
                RTreePage<K, V> child = dbConfig.getPageBuffer().fixPage(cID, factory, false,
                    tree.internalOwner);
                assert child != null;
                assert child.getPageMBR().equals(cMBR) : this + ":" + cMBR + " != "
                    + child.getPageMBR() + "@" + child;
                assert child.getPageMBR().equals(child.countContentMBR()) : child.getPageMBR()
                    + " != " + child.countContentMBR();
                child.checkConsistency();
                dbConfig.getPageBuffer().unfix(child, tree.internalOwner);
            }
        }
    }

    @Override
    public boolean containsChild(PageID childID) {
        for (Pair<MBR<K>, PageValue<?>> entry : contents) {
            if (entry.getSecond().equals(childID)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected V removeEntry(MBR<K> key) {
        return (V) contents.removeFirst(key);
    }

    @Override
    public int getTypeIdentifier() {
        return PAGE_IDENTIFIER;
    }
}
