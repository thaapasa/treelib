package fi.hut.cs.treelib.mdtree;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.BulkLoadable;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.concurrency.NoopLatchManager;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.tuska.util.Converter;
import fi.tuska.util.Pair;

/**
 * Ordered multi-dimension database, abstract base class for multidimension
 * databases that have an enforced ordering for the entries.
 * 
 * @author thaapasa
 */
public abstract class OMDDatabase<K extends Key<K>, V extends PageValue<?>, L extends Key<L>, P extends OMDPage<K, V, L>>
    extends AbstractMDDatabase<K, V, L, OMDPage<K, V, L>> implements BulkLoadable<MBR<K>, V> {

    private static final Logger log = Logger.getLogger(OMDDatabase.class);

    protected OMDDatabase(int bufferSize, SMOPolicy smoPolicy, PageStorage pageStorage,
        MBR<K> keyPrototype, V valuePrototype) {
        super(bufferSize, smoPolicy, pageStorage, NoopLatchManager.instance(), keyPrototype,
            valuePrototype);
    }

    @Override
    public OMDTree<K, V, L> getDatabaseTree() {
        return (OMDTree<K, V, L>) super.getDatabaseTree();
    }

    public Comparator<Pair<MBR<K>, V>> getEntryComparator() {
        final Converter<MBR<K>, L> converter = getDatabaseTree().getSearchKeyCreator();
        return new Comparator<Pair<MBR<K>, V>>() {
            @Override
            public int compare(Pair<MBR<K>, V> o1, Pair<MBR<K>, V> o2) {
                L l1 = converter.convert(o1.getFirst());
                L l2 = converter.convert(o2.getFirst());
                int o = l1.compareTo(l2);
                if (o != 0)
                    return o;
                o = o1.compareTo(o2);
                if (o != 0)
                    return o;
                return o1 == o2 ? 0 : 1;
            }
        };
    }

    @Override
    public void bulkLoad(Iterable<Pair<MBR<K>, V>> keys, Transaction<MBR<K>, V> tx) {
        Comparator<Pair<MBR<K>, V>> comp = getEntryComparator();
        // Sort keys by minimum the tree ordering coordinate
        Pair<MBR<K>, V>[] keyArray = getSortedArray(keys, comp);
        final int keyCount = keyArray.length;

        log.info("Bulk-loading " + keyCount + " keys to " + getDatabaseTree().getName());
        List<Pair<MBR<K>, V>> keyList = Arrays.asList(keyArray);
        OMDPage<K, V, L> root = getDatabaseTree().bulkLoad(keyList, comp);
        getDatabaseTree().attachRoot(root, tx);
        getPageBuffer().unfix(root, tx);
    }
}
