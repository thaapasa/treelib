package fi.hut.cs.treelib.data;

import java.util.Iterator;
import java.util.Random;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.action.Action;
import fi.hut.cs.treelib.action.BeginTransactionAction;
import fi.hut.cs.treelib.action.CommitTransactionAction;
import fi.hut.cs.treelib.action.DeleteAction;
import fi.hut.cs.treelib.action.InsertAction;
import fi.hut.cs.treelib.action.QueryAction;
import fi.hut.cs.treelib.action.RangeQueryAction;
import fi.hut.cs.treelib.common.LongKey;

public class ActionGenerator<K extends Key<K>, V extends PageValue<?>> implements
    Iterator<Action<K, V>>, Iterable<Action<K, V>> {

    private final Random random = new Random();
    private final Generator<V> valueGenerator;
    private final DataGenerator<K> keyGenerator;
    private final LongKey minTxLen;
    private final LongKey maxTxLen;
    private long posInTx;
    private long curTxLimit;
    private final long txAmount;
    private long curTx;
    /** From 0 to 1. */
    private double deleteProbability = 0;
    private double insertProbability = 0;
    private double rangeQueryProbability = 0;
    private double rangeSize = 0.2;

    public ActionGenerator(K bounds, K minLimit, K maxLimit, long minTxLen, long maxTxLen,
        long txAmount, Generator<V> valueGenerator) {
        this.keyGenerator = new DataGenerator<K>(bounds, minLimit, maxLimit);
        this.minTxLen = new LongKey(minTxLen);
        this.maxTxLen = new LongKey(maxTxLen);
        this.txAmount = txAmount;
        this.valueGenerator = valueGenerator;
        this.posInTx = 0;
        this.curTxLimit = 0;
        this.curTx = 0;
    }

    public void setDeleteProbability(double deleteProbability) {
        this.deleteProbability = deleteProbability;
    }

    public void setInsertProbability(double insertProbability) {
        this.insertProbability = insertProbability;
    }

    public void setRangeQueryProbability(double rangeQueryProbability) {
        this.rangeQueryProbability = rangeQueryProbability;
    }

    public void setRangeSize(double rangeSize) {
        this.rangeSize = rangeSize;
    }

    public K getKeyPrototype() {
        return keyGenerator.getPrototype();
    }

    public V getValuePrototype() {
        return valueGenerator.getPrototype();
    }

    public void applyActions(Iterator<Action<K, V>> actions) {
        keyGenerator.applyActions(actions);
    }

    @Override
    public boolean hasNext() {
        return curTx < txAmount;
    }

    public int getAliveKeyCount() {
        return keyGenerator.getAliveKeyCount();
    }

    public int getAllInsertedKeyCount() {
        return keyGenerator.getAllInsertedKeyCount();
    }

    private enum ActionType {
        INSERT, DELETE, QUERY, RANGE_QUERY
    };

    private ActionType getNextActionType() {
        double nextAction = random.nextDouble();
        if (nextAction < insertProbability)
            return ActionType.INSERT;
        nextAction -= insertProbability;
        if (nextAction < deleteProbability)
            return ActionType.DELETE;
        nextAction -= deleteProbability;
        if (nextAction < rangeQueryProbability)
            return ActionType.RANGE_QUERY;
        else
            return ActionType.QUERY;
    }

    @Override
    public Action<K, V> next() {
        Action<K, V> action;
        if (posInTx == 0) {
            action = new BeginTransactionAction<K, V>();
            this.curTxLimit = LongKey.PROTOTYPE.random(this.minTxLen, this.maxTxLen, random)
                .longValue();
            posInTx++;
        } else {
            if (posInTx > curTxLimit) {
                // Commit tx
                action = new CommitTransactionAction<K, V>();
                keyGenerator.commit();
                posInTx = 0;
                curTx++;
            } else {
                action = null;

                ActionType type = getNextActionType();
                if (type == ActionType.DELETE) {
                    K toDelete = keyGenerator.delete();
                    if (toDelete != null) {
                        action = new DeleteAction<K, V>(toDelete);
                    }
                    // If toDelete is null, just continue to create an insert
                    // action instead break;
                } else if (type == ActionType.QUERY) {
                    K toQuery = keyGenerator.query();
                    if (toQuery != null) {
                        action = new QueryAction<K, V>(toQuery);
                    }
                    // If toQuery is null, just continue to create an insert
                    // action instead
                } else if (type == ActionType.RANGE_QUERY) {
                    K min = keyGenerator.query();
                    if (min != null) {
                        K max = min.add(keyGenerator.getRangeSize().multiply(rangeSize));
                        assert max != null;
                        if (min.compareTo(max) > 0) {
                            K temp = min;
                            min = max;
                            max = temp;
                        }
                        action = new RangeQueryAction<K, V>(min, max);
                    }
                }

                // Default (can always insert a new entry)
                if (action == null) {
                    action = new InsertAction<K, V>(keyGenerator.insert(), valueGenerator
                        .generate());
                }
                posInTx++;
            }
        }
        return action;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove() not supported");
    }

    @Override
    public Iterator<Action<K, V>> iterator() {
        return this;
    }

}
