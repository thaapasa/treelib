package fi.hut.cs.treelib.mdtree;

import java.lang.reflect.Array;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.MVDatabase;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.common.AbstractDatabase;
import fi.hut.cs.treelib.common.AbstractMDPage;
import fi.hut.cs.treelib.common.AbstractMDTree;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.concurrency.LatchManager;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.util.Predicate;
import fi.tuska.util.Callback;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Pair;

public abstract class AbstractMDDatabase<K extends Key<K>, V extends PageValue<?>, L extends Key<L>, P extends AbstractMDPage<K, V, L, P>>
    extends AbstractDatabase<MBR<K>, V, P> implements MDDatabase<K, V, P> {

    private static final Logger log = Logger.getLogger(AbstractMDDatabase.class);

    protected AbstractMDDatabase(int bufferSize, SMOPolicy smoPolicy, PageStorage pageStorage,
        LatchManager latchManager, MBR<K> keyPrototype, V valuePrototype) {
        super(bufferSize, pageStorage, smoPolicy, latchManager, keyPrototype, valuePrototype);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AbstractMDTree<K, V, L, P> getDatabaseTree() {
        return (AbstractMDTree<K, V, L, P>) super.getDatabaseTree();
    }

    @Override
    public boolean isMultiDimension() {
        return true;
    }

    @Override
    public boolean isMultiVersion() {
        return false;
    }

    @Override
    public void traversePages(Predicate<KeyRange<MBR<K>>> predicate,
        Callback<Page<MBR<K>, V>> operation, Owner owner) {
        throw new UnsupportedOperationException("Use traverseMDPages() instead");
    }

    @Override
    public void traverseMDPages(Predicate<MBR<K>> predicate, Callback<Page<MBR<K>, V>> operation,
        Owner owner) {
        getDatabaseTree().traverseMDPages(predicate, operation, owner);
    }

    @Override
    public MVDatabase<MBR<K>, V, P> getMVDatabase() {
        throw new UnsupportedOperationException("MD-trees are not multiversion");
    }

    @Override
    public Set<Integer> getSeparateRootedVersions() {
        throw new UnsupportedOperationException("There are no multiple versions in MD-trees");
    }

    @SuppressWarnings("unchecked")
    protected Pair<MBR<K>, V>[] getSortedArray(Iterable<Pair<MBR<K>, V>> keys,
        Comparator<Pair<MBR<K>, V>> ordering) {
        // Create a sorted set that is ordered by the min Y coordinate
        TreeSet<Pair<MBR<K>, V>> sortedKeys = new TreeSet<Pair<MBR<K>, V>>(ordering);
        // Insertion-sort the keys
        CollectionUtils.addAll(sortedKeys, keys);
        // All the keys are now ordered by min Y coordinate
        return sortedKeys.toArray((Pair<MBR<K>, V>[]) Array.newInstance(Pair.class, 0));
    }

    /**
     * Adjusts the slice end position in an STR split so that the list is
     * split properly
     */
    protected int adjustSTRSliceEndPos(List<Pair<MBR<K>, V>> keyList, int pos,
        int separatorDimension) {
        int size = keyList.size();
        // Scan to a position where the MBR min Y's differ
        while (pos < (size - 1)
            && keyList.get(pos).getFirst().getMin().get(separatorDimension).equals(
                keyList.get(pos - 1).getFirst().getMin().get(separatorDimension)))
            pos++;
        return pos;
    }

    /**
     * Splits a sorted list into a number of slices for the STR algorithm
     * 
     * @param separatorDimension used for checking that the slices differ in
     * some dimension. Set to 1 to force slice boundaries to have differing
     * Y-values, 0 for X-values and so on. Set to -1 to skip boundary checking
     * (for R-trees, for example)
     */
    protected void createSTRSlices(List<Pair<MBR<K>, V>> keyList, int pageCapacity,
        int separatorDimension, Callback<List<Pair<MBR<K>, V>>> callback) {
        final int keyCount = keyList.size();
        int P = (int) Math.ceil((double) keyCount / pageCapacity);
        int S = (int) Math.ceil(Math.sqrt(P));
        int sliceSize = (keyCount / S) + (keyCount % S != 0 ? 1 : 0);
        int startPos = 0;

        log.info("Creating " + S + " slices from " + keyCount + " entries (" + pageCapacity
            + " entries per page); slice size is " + sliceSize);
        for (int i = 0; i < S; i++) {
            int endPos = Math.min((i + 1) * sliceSize, keyCount);

            // Adjust end position so that there are no same min coordinates
            // in the slices.
            if (separatorDimension >= 0)
                endPos = adjustSTRSliceEndPos(keyList, endPos, separatorDimension);

            List<Pair<MBR<K>, V>> subList = keyList.subList(startPos, endPos);

            log.info("Creating slice from " + startPos + " (" + keyList.get(startPos) + ") to "
                + (endPos - 1) + "(" + keyList.get(endPos - 1) + ") (" + subList.size()
                + " entries)");

            callback.callback(subList);
            startPos = endPos;
        }
    }

    protected final Comparator<Pair<MBR<K>, V>> SORT_BY_Y = new Comparator<Pair<MBR<K>, V>>() {
        @Override
        public int compare(Pair<MBR<K>, V> kv1, Pair<MBR<K>, V> kv2) {
            MBR<K> mbr1 = kv1.getFirst();
            MBR<K> mbr2 = kv2.getFirst();
            int c = mbr1.getMin().get(1).compareTo(mbr2.getMin().get(1));
            if (c != 0)
                return c;
            c = mbr1.getMax().get(1).compareTo(mbr2.getMax().get(1));
            if (c != 0)
                return c;
            c = mbr1.getMin().get(0).compareTo(mbr2.getMin().get(0));
            if (c != 0)
                return c;
            c = mbr1.getMax().get(0).compareTo(mbr2.getMax().get(0));
            if (c != 0)
                return c;
            // Two different instances of the same MBR must not be equal so
            // that we can have multiple objects with the same MBR
            return mbr1 == mbr2 ? 0 : 1;
        }
    };

    protected final Comparator<Pair<MBR<K>, V>> SORT_BY_X = new Comparator<Pair<MBR<K>, V>>() {
        @Override
        public int compare(Pair<MBR<K>, V> kv1, Pair<MBR<K>, V> kv2) {
            MBR<K> mbr1 = kv1.getFirst();
            MBR<K> mbr2 = kv2.getFirst();
            int c = mbr1.getMin().get(0).compareTo(mbr2.getMin().get(0));
            if (c != 0)
                return c;
            c = mbr1.getMax().get(0).compareTo(mbr2.getMax().get(0));
            if (c != 0)
                return c;
            c = mbr1.getMin().get(1).compareTo(mbr2.getMin().get(1));
            if (c != 0)
                return c;
            c = mbr1.getMax().get(1).compareTo(mbr2.getMax().get(1));
            if (c != 0)
                return c;
            // Two different instances of the same MBR must not be equal so
            // that we can have multiple objects with the same MBR
            return mbr1 == mbr2 ? 0 : 1;
        }
    };
}
