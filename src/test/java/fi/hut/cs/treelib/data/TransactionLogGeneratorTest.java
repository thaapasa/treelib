package fi.hut.cs.treelib.data;

import fi.hut.cs.treelib.TreeLibTest;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;

public class TransactionLogGeneratorTest extends TreeLibTest {

    public void testGeneration() {
        ActionGenerator<IntegerKey, IntegerValue> act = new ActionGenerator<IntegerKey, IntegerValue>(
            getIK(0), getIK(0), getIK(100), 5, 20, 5, new IntegerValueGenerator());
        act.setInsertProbability(0.3);
        act.setDeleteProbability(0.2);
        act.setRangeQueryProbability(0.2);

        TransactionLogGenerator<IntegerKey, IntegerValue> gen = new TransactionLogGenerator<IntegerKey, IntegerValue>(
            act, 20, System.out, System.err);

        gen.generate();
    }
}
