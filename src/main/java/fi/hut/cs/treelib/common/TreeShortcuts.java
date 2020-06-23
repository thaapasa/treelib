package fi.hut.cs.treelib.common;

import java.util.List;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVTree;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.util.KeyRangePredicate;
import fi.hut.cs.treelib.util.MVKeyRangePredicate;
import fi.tuska.util.Callback;
import fi.tuska.util.Converter;
import fi.tuska.util.Counter;
import fi.tuska.util.Holder;
import fi.tuska.util.Pair;
import fi.tuska.util.TypeConvertingCallback;

public class TreeShortcuts {

    public static <K extends Key<K>, V extends PageValue<?>> List<Pair<K, V>> getRange(
        Tree<K, V, ?> tree, final KeyRange<K> range, Transaction<K, V> tx) {

        ListCreatingCallback<Pair<K, V>> lc = new ListCreatingCallback<Pair<K, V>>();
        tree.getRange(range, lc, tx);
        return lc.getList();
    }

    public static <K extends Key<K>, V extends PageValue<?>> List<Pair<K, V>> getRange(
        MVTree<K, V, ?> tree, final KeyRange<K> range, Transaction<K, V> tx) {

        ListCreatingCallback<Pair<K, V>> lc = new ListCreatingCallback<Pair<K, V>>();
        tree.getRange(range, lc, tx);
        return lc.getList();
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> boolean delete(
        Tree<K, V, P> tree, K key, Transaction<K, V> tx) {
        PagePath<K, V, P> path = new PagePath<K, V, P>(true);
        boolean result = tree.delete(key, path, tx);
        tree.getPageBuffer().unfix(path, tx);
        return result;
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> boolean insert(
        Tree<K, V, P> tree, K key, V value, Transaction<K, V> tx) {
        PagePath<K, V, P> path = new PagePath<K, V, P>(true);
        boolean result = tree.insert(key, value, path, tx);
        tree.getPageBuffer().unfix(path, tx);
        return result;
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> Pair<K, V> nextEntry(
        Tree<K, V, P> tree, final K key, Transaction<K, V> tx) {

        // Simple implentation: use the range query to find the next entry,
        // stop when the first entry is found
        KeyRange<K> range = new KeyRangeImpl<K>(key.nextKey(), key.getMaxKey());
        final Holder<Pair<K, V>> entry = new Holder<Pair<K, V>>();

        tree.getRange(range, new Callback<Pair<K, V>>() {
            @Override
            public boolean callback(Pair<K, V> item) {
                assert item.getFirst().compareTo(key) > 0;
                entry.setValue(item);
                // Item found, stop search
                return false;
            }
        }, tx);
        return entry.getValue();
    }

    public static <K extends Key<K>, V extends PageValue<?>> Converter<Page<K, V>, PageID> getPageIDConverter(
        Tree<K, V, ?> tree) {
        return new Converter<Page<K, V>, PageID>() {
            @Override
            public PageID convert(Page<K, V> page) {
                return page.getPageID();
            }
        };
    }

    /**
     * Counts the number of pages in the given single-version tree.
     * 
     * @param tree the tree
     * @param discretePages set to true if the tree traversal may process same
     * pages twice
     * @return the number of pages in the tree
     */
    public static <K extends Key<K>, V extends PageValue<?>> long countPages(
        AbstractTree<K, V, ?> tree, boolean discretePages) {
        CounterCallback<PageID> idcc = new CounterCallback<PageID>(discretePages);
        Callback<Page<K, V>> pcc = new TypeConvertingCallback<Page<K, V>, PageID>(
            getPageIDConverter(tree), idcc);
        tree.traversePages(new KeyRangePredicate<K>(), pcc, tree.internalOwner);
        return idcc.getCount();
    }

    /**
     * Counts the number of pages in the given multiversion tree.
     * 
     * @param tree the tree
     * @param discretePages set to true if the tree traversal may process same
     * pages twice
     * @return the number of pages in the tree
     */
    public static <K extends Key<K>, V extends PageValue<?>> long countMVPages(
        AbstractMVTree<K, V, ?> tree, boolean discretePages) {
        CounterCallback<PageID> idcc = new CounterCallback<PageID>(discretePages);
        Callback<Page<K, V>> pcc = new TypeConvertingCallback<Page<K, V>, PageID>(
            getPageIDConverter(tree), idcc);
        tree.traverseMVPages(new MVKeyRangePredicate<K>(tree.getKeyPrototype()), pcc,
            tree.internalOwner);
        return idcc.getCount();
    }

    /**
     * Counts the number of entries in the given (single-version) tree. The
     * tree traversal must not process same page multiple times, otherwise the
     * entries will be duplicated also in the result.
     * 
     * @param tree the tree to process
     * @return the number of entries in the tree
     */
    public static <K extends Key<K>, V extends PageValue<?>> long countEntries(
        AbstractTree<K, V, ?> tree) {
        final Counter c = new Counter();
        tree.traversePages(new KeyRangePredicate<K>(), new Callback<Page<K, V>>() {
            @Override
            public boolean callback(Page<K, V> page) {
                c.advance(page.getEntryCount());
                return true;
            }
        }, tree.internalOwner);
        return c.getCount();
    }
}
