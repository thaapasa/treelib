package fi.hut.cs.treelib.data;

import fi.hut.cs.treelib.TreeLibDBTest;
import fi.hut.cs.treelib.action.Action;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;

public class ActionGeneratorTest extends TreeLibDBTest<IntegerKey, IntegerValue> {

    public ActionGeneratorTest() {
        super(IntegerKey.PROTOTYPE, IntegerValue.PROTOTYPE);
    }

    public void testCreateActions() {
        ActionGenerator<IntegerKey, IntegerValue> gen = new ActionGenerator<IntegerKey, IntegerValue>(
            getIK(0), getIK(0), getIK(100), 5, 20, 5, new IntegerValueGenerator());
        gen.setInsertProbability(0.3);
        gen.setDeleteProbability(0.2);
        gen.setRangeQueryProbability(0.2);

        for (Action<IntegerKey, IntegerValue> action : gen) {
            System.out.println(action);
        }
    }

}
