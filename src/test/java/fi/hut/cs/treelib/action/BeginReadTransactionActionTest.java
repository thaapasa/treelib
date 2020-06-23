package fi.hut.cs.treelib.action;

import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerValue;
import junit.framework.TestCase;

public class BeginReadTransactionActionTest extends TestCase {

    public void testLog() {
        BeginReadTransactionAction<IntegerKey, IntegerValue> key = new BeginReadTransactionAction<IntegerKey, IntegerValue>(
            35);
        String log = key.writeToLog();
        assertEquals("br;35", log);

        key = key.readFromLog("br;35");
        assertEquals(35, key.getReadVersion());

        key = key.readFromLog("br");
        assertEquals(0, key.getReadVersion());

        key = key.readFromLog("br;-1");
        assertEquals(-1, key.getReadVersion());
    }
}
