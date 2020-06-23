package fi.hut.cs.treelib.util;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Operation;
import fi.hut.cs.treelib.stats.Statistics.Value;

public class MDDBUtils {

    /**
     * Logs an MD page split.
     */
    public static <K extends Key<K>> void logSplit(StatisticsLogger stats, MBR<K> pageMBR,
        MBR<K> siblingMBR, double entrySplitRatio) {
        if (stats.isStarted()) {
            stats.log(Operation.OP_PAGE_SPLIT);
            MBR<K> splitIntersection = pageMBR.intersection(siblingMBR);
            float area = splitIntersection != null ? splitIntersection.getArea().toFloat() : 0;

            MBR<K> total = pageMBR.extend(siblingMBR);
            float totalArea = total.getArea().toFloat();

            stats.log(Value.VAL_SPLIT_OVERLAP_AREA, area);
            stats.log(Value.VAL_SPLIT_OVERLAP_RATIO, area / totalArea);
            stats.log(Value.VAL_SPLIT_RATIO, entrySplitRatio);
        }
    }

    /**
     * Logs a page split axis used.
     * 
     * @param axis the axis
     */
    public static void logSplitAxis(StatisticsLogger stats, int axis) {
        switch (axis) {
        case 0:
            stats.log(Operation.OP_SPLIT_BY_X);
            break;
        case 1:
            stats.log(Operation.OP_SPLIT_BY_Y);
            break;
        case 2:
            stats.log(Operation.OP_SPLIT_BY_Z);
            break;
        default:
            stats.log(Operation.OP_SPLIT_BY_OTHER);
            break;
        }
    }

}
