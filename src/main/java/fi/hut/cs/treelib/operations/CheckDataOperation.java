package fi.hut.cs.treelib.operations;

import fi.hut.cs.treelib.Coordinate;
import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Action;

public class CheckDataOperation<K extends Key<K>> implements Operation<K> {

    private StatisticsLogger statisticsLogger;
    private K keyPrototype;
    private MBR<K> mbrKeyProto;

    @SuppressWarnings("unchecked")
    public CheckDataOperation(Database<K, IntegerValue, ?> database,
        StatisticsLogger statisticsLogger) {
        this.statisticsLogger = statisticsLogger;
        this.keyPrototype = database.getDatabaseTree().getKeyPrototype();
        if (keyPrototype instanceof MBR) {
            mbrKeyProto = (MBR<K>) keyPrototype;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Iterable<K> keys) {
        statisticsLogger.newAction(Action.ACTION_SPECIAL);

        long totalEqualKeys = 0;
        long curEqualC = 0;
        long maxEqualC = 0;
        K prevKey = null;

        Coordinate<K> prevCoord = null;
        long totalEqualCoord = 0;
        long curEqualCoordC = 0;
        long maxEqualCoordC = 0;

        for (K key : keys) {
            if (prevKey != null && key.equals(prevKey)) {
                if (maxEqualC == 0) {
                    // First one is always missed
                    totalEqualKeys++;
                    curEqualC++;
                }
                totalEqualKeys++;
                curEqualC++;
                maxEqualC = Math.max(maxEqualC, curEqualC);
            } else {
                curEqualC = 0;
            }
            if (mbrKeyProto != null) {
                Coordinate<K> coord = ((MBR<K>) key).getMin();
                if (prevCoord != null && coord.equals(prevCoord)) {
                    if (maxEqualCoordC == 0) {
                        // First one is always missed
                        totalEqualCoord++;
                        curEqualCoordC++;
                    }
                    totalEqualCoord++;
                    curEqualCoordC++;
                    maxEqualCoordC = Math.max(maxEqualCoordC, curEqualCoordC);
                } else {
                    curEqualCoordC = 0;
                }
                prevCoord = coord;
            }
            prevKey = key;
        }

        System.out.println("Equal keys: " + totalEqualKeys);
        System.out.println("Longest consecutive amount of equal keys: " + maxEqualC);

        if (mbrKeyProto != null) {
            System.out.println("MBR's with same xmin,xmax: " + totalEqualCoord);
            System.out.println("Longest consecutive of these: " + maxEqualCoordC);
        }

    }

    @Override
    public boolean requiresKeys() {
        return false;
    }

}
