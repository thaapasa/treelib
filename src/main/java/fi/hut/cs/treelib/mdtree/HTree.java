package fi.hut.cs.treelib.mdtree;

import fi.hut.cs.treelib.Coordinate;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.LongKey;
import fi.hut.cs.treelib.mdtree.OMDTreeOperations.SplitType;
import fi.tuska.util.Converter;
import fi.tuska.util.geom.HilbertCurve;

/**
 * Implements the Hilbert R-tree based on the ordered multidimensional tree.
 * 
 * <p>
 * In Hilbert R-trees, the data item MBRs are sorted based on their order on
 * the Hilbert Curve (the space filling curve). This implementation uses a
 * generic conversion algorithm for calculating 62-bit long values (values on
 * Hilbert curve of order 31) for any floating-point MBRs.
 * 
 * <p>
 * See more discussion below (at the converter function).
 * 
 * @author thaapasa
 */
public class HTree<K extends Key<K>, V extends PageValue<?>> extends OMDTree<K, V, LongKey> {

    private final int hilbertOrder;

    /**
     * Currently testing a generic H-tree that maps all floats to integers by
     * bit-conversion.
     */
    public HTree(PageID rootPageID, DatabaseConfiguration<MBR<K>, V> dbConfig) {
        super("htree-31", "Hilbert R-tree (order: 31)", LongKey.PROTOTYPE, rootPageID,
            createSearchKeyCreator(31, dbConfig.getKeyPrototype()), dbConfig);

        if (keyPrototype.getDimensions() != 2)
            throw new IllegalArgumentException(
                "Hilbert R-trees can only store two-dimensional data (currently)");

        this.hilbertOrder = 31;
        // this.maxHilbertCoord = (1L << hilbertOrder) - 1;

        getOperations().setSplitType(SplitType.HALF);
    }

    /**
     * The algorithm works as follows:
     * 
     * <ul>
     * <li>X, Y = the center point of the MBR (float values)
     * 
     * <li>Take the bits of the float values and treat them as bits of integer
     * values
     * <ul>
     * <li>This is a one-to-one mapping
     * 
     * <li>The represented numerical values are different, of course, but the
     * ordering is preserved, so this does not matter
     * </ul>
     * <li>Convert the integers to long values
     * 
     * <li>Add the absolute value of the largest negative possible value of
     * integers to X and Y to force them to be positive
     * 
     * <li>Now, X and Y are long values that are guaranteed to be positive,
     * and use 32 bits. The ordering is also the same as with the original
     * float values, so that X < Y (floats) implies X < Y (converted long
     * values)
     * 
     * <li>Calculate the Hilbert curve value for the converted values using a
     * Hilbert curve of order 32. This creates a long value that has 64 bits.
     * This long values is the search key used in Hilbert R-trees.
     * </ul>
     * 
     * <p>
     * Note that since the implementation uses Java, and there are no unsigned
     * integer or long values, the actual implementation converts the floats
     * to only 31 bits (by discarding the LSB of the converted integer value),
     * and uses a Hilbert curve of order 31 to generate a long value that uses
     * at most 62 bits. Thus all the values used in the calculations are
     * always positive.
     */
    private static <K extends Key<K>> Converter<MBR<K>, LongKey> createSearchKeyCreator(
        final int hilbertOrder, MBR<K> proto) {
        return new Converter<MBR<K>, LongKey>() {
            @Override
            public LongKey convert(MBR<K> mbr) {
                if (mbr == null)
                    return LongKey.MAX_KEY;

                Coordinate<IntegerKey> c = convertToIntegerCoordinate(mbr);

                long value = HilbertCurve.encode(getHilbertLong(c.get(0).intValue()),
                    getHilbertLong(c.get(1).intValue()), hilbertOrder);
                return new LongKey(value);
            }
        };
    }

    protected static long getHilbertLong(int value) {
        long val = value + (-(long) Integer.MIN_VALUE);
        // val is now an unsigned number, contained in 32 bits
        assert val >= 0;
        assert (val & (~0xffffffffL)) == 0;
        // Shorten val to 31 bits by discarding the last bit
        return val >> 1;
    }

    /**
     * Scales the data MBR to the normalized and scaled range that can be used
     * to calculate the order of the point in the Hilbert Curve.
     * 
     * @param dataMBR the data MBR
     * @return the coordinate of the center point of the scaled MBR in the
     * normalized coorinate system
     */
    protected static <K extends Key<K>> Coordinate<IntegerKey> convertToIntegerCoordinate(
        MBR<K> dataMBR) {
        Coordinate<K> centerPoint = dataMBR.getMiddle();

        int x = 0;
        int y = 0;
        if (dataMBR.getLow(0) instanceof IntegerKey) {
            x = centerPoint.get(0).toInt();
            y = centerPoint.get(1).toInt();
        } else {
            // Generic conversion from float representation
            x = Float.floatToIntBits(centerPoint.get(0).toFloat());
            y = Float.floatToIntBits(centerPoint.get(1).toFloat());
        }

        return new Coordinate<IntegerKey>(new IntegerKey(x), new IntegerKey(y));
    }

    public int getHilbertOrder() {
        return hilbertOrder;
    }

}
