package fi.hut.cs.treelib.common;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.OrderedTransaction;
import fi.hut.cs.treelib.OrderedTree;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.stats.Statistics.Action;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.tuska.util.Pair;

public class OrderedTransactionImpl<K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>>
    extends TransactionImpl<K, V, P> implements OrderedTransaction<K, V> {

    private static final Logger log = Logger.getLogger(OrderedTransactionImpl.class);

    public OrderedTransactionImpl(Database<K, V, P> database, int readVersionID, boolean readOnly) {
        super(database, readVersionID, readOnly);
    }

    protected OrderedTree<K, V, P> getDatabaseTree() {
        OrderedTree<K, V, P> tree = (OrderedTree<K, V, P>) database.getDatabaseTree();
        return tree;
    }

    @Override
    public Pair<K, V> floorEntry(K key) {
        if (committed)
            throw new IllegalStateException("Already committed");

        if (log.isDebugEnabled()) {
            log.debug(database.getDatabaseTree().getName() + " action: query " + key);
        }
        database.getStatisticsLogger().newAction(Action.ACTION_QUERY_FLOOR);

        Pair<K, V> val = getDatabaseTree().floorEntry(key, this);

        if (val != null) {
            database.getStatisticsLogger().log(Operation.OP_QUERY_FOUND_OBJECT);
        }
        runAfterAction();
        return val;
    }

    @Override
    public Pair<K, V> nextEntry(K key) {
        if (committed)
            throw new IllegalStateException("Already committed");

        if (log.isDebugEnabled()) {
            log.debug(database.getDatabaseTree().getName() + " action: query " + key);
        }
        database.getStatisticsLogger().newAction(Action.ACTION_QUERY_NEXT);

        PagePath<K, V, P> path = new PagePath<K, V, P>(true);
        Pair<K, V> val = getDatabaseTree().nextEntry(key, path, this);
        database.getPageBuffer().unfix(path, this);

        if (val != null) {
            database.getStatisticsLogger().log(Operation.OP_QUERY_FOUND_OBJECT);
        }
        runAfterAction();
        return val;
    }

}
