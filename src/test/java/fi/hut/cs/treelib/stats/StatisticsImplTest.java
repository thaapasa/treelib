package fi.hut.cs.treelib.stats;

import junit.framework.TestCase;
import fi.hut.cs.treelib.stats.Statistics.Action;
import fi.hut.cs.treelib.stats.Statistics.Operation;

public class StatisticsImplTest extends TestCase {

    public void testPausing() {
        StatisticsImpl s = new StatisticsImpl();

        assertNull(s.operationCurrent.get(Operation.OP_BUFFER_FIX));
        s.log(Operation.OP_BUFFER_FIX);
        assertNull(s.operationCurrent.get(Operation.OP_BUFFER_FIX));

        s.startStatistics();
        s.log(Operation.OP_BUFFER_FIX);
        assertEquals(1, s.operationCurrent.get(Operation.OP_BUFFER_FIX).longValue());

        s.pauseStatistics();
        assertEquals(1, s.operationCurrent.get(Operation.OP_BUFFER_FIX).longValue());
        s.log(Operation.OP_BUFFER_FIX);
        assertEquals(1, s.operationCurrent.get(Operation.OP_BUFFER_FIX).longValue());
        s.log(Operation.OP_BUFFER_FIX);
        assertEquals(1, s.operationCurrent.get(Operation.OP_BUFFER_FIX).longValue());

        s.continueStatistics();
        assertEquals(1, s.operationCurrent.get(Operation.OP_BUFFER_FIX).longValue());
        s.log(Operation.OP_BUFFER_FIX);
        assertEquals(2, s.operationCurrent.get(Operation.OP_BUFFER_FIX).longValue());
    }

    public void testDeviation() {
        StatisticsImpl s = new StatisticsImpl();
        s.startStatistics();

        int[] opcounts = new int[] { 5, 4, 7, 9, 5, 2, 4, 4 };

        // assertEquals(5.0d, dev.getMean(), 0.0001);
        // assertEquals(2.0d, dev.getDeviation(), 0.0001);
        // assertEquals(2.0d, dev.getMin());
        // assertEquals(9.0d, dev.getMax());
        for (int o = 0; o < opcounts.length; o++) {
            s.newAction(Action.ACTION_QUERY);
            s.log(Operation.OP_BUFFER_READ);
            for (int i = 0; i < opcounts[o]; i++) {
                if (i % 2 == 0)
                    s.log(Operation.OP_BUFFER_WRITE);
                s.log(Operation.OP_BUFFER_FIX);
            }
        }

        Statistics st = s.getStatistics();
        assertEquals(5.0d, st.getOperationAverage(Operation.OP_BUFFER_FIX), 0.0001);
        assertEquals(2.0d, st.getOperationDeviation(Operation.OP_BUFFER_FIX), 0.0001);
        assertEquals(2.0d, st.getOperationMin(Operation.OP_BUFFER_FIX));
        assertEquals(9.0d, st.getOperationMax(Operation.OP_BUFFER_FIX));
    }

}
