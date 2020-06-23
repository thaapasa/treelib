package fi.hut.cs.treelib.action;

import junit.framework.TestCase;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;

public class RangeQueryActionTest extends TestCase {

    private ActionReader<IntegerKey, IntegerValue> reader = new ActionReader<IntegerKey, IntegerValue>(
        IntegerKey.PROTOTYPE, IntegerValue.PROTOTYPE);

    public void testWriteInt() {
        KeyRange<IntegerKey> range = IntegerKey.ENTIRE_RANGE;

        String written = RangeQueryAction.writeActionToLog(range, null);
        assertEquals("r;-INF;INF", written);
        RangeQueryAction<IntegerKey, IntegerValue> action = (RangeQueryAction<IntegerKey, IntegerValue>) reader
            .read(written);

        assertEquals(IntegerKey.ENTIRE_RANGE, action.getKeyRange());

        written = RangeQueryAction.writeActionToLog(range, 5l);
        assertEquals("r;-INF;INF;5", written);
        action = (RangeQueryAction<IntegerKey, IntegerValue>) reader.read(written);
        assertEquals(5l, action.getLimitCount().longValue());
    }

}
