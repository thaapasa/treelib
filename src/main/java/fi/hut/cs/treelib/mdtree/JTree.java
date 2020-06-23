package fi.hut.cs.treelib.mdtree;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Coordinate;
import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.mdtree.OMDTreeOperations.SplitType;
import fi.tuska.util.Converter;

/**
 * Implements the J-tree, based on the ordered multidimensional tree.
 * 
 * <p>
 * J-tree uses the minimum coordinates of the data item MBRs to sort the data
 * MBRs.
 * 
 * @author thaapasa
 */
public class JTree<K extends Key<K>, V extends PageValue<?>> extends OMDTree<K, V, Coordinate<K>> {

    private static final Logger log = Logger.getLogger(JTree.class);
    private final OMDPage<K, V, Coordinate<K>> leafProto;

    public JTree(SplitType splitType, PageID rootPageID, DatabaseConfiguration<MBR<K>, V> dbConfig) {
        this(splitType, rootPageID, createSearchKeyCreator(dbConfig.getKeyPrototype()), dbConfig);
    }

    public JTree(SplitType splitType, PageID rootPageID,
        Converter<MBR<K>, Coordinate<K>> searchKeyCreator,
        DatabaseConfiguration<MBR<K>, V> dbConfig) {
        super("jtree-" + splitType.toString().toLowerCase(), "J-tree", dbConfig.getKeyPrototype()
            .getMax(), rootPageID, searchKeyCreator, dbConfig);

        getOperations().setSplitType(splitType);

        // Create a prototype leaf page
        leafProto = new OMDPage<K, V, Coordinate<K>>(this, PageID.INVALID_PAGE_ID,
            getSearchKeyCreator());
        leafProto.format(1);
    }

    /**
     * In J-tree, the minimum coordinate (lower left corner) of the MBR is
     * used to sort the data items.
     */
    public static <K extends Key<K>> Converter<MBR<K>, Coordinate<K>> createSearchKeyCreator(
        final MBR<K> proto) {
        return new Converter<MBR<K>, Coordinate<K>>() {
            private final Coordinate<K> maxCoord = proto.getMax().getMaxKey();

            @Override
            public Coordinate<K> convert(MBR<K> mbr) {
                if (mbr == null)
                    return maxCoord;
                // The min coordinate of the MBR is the search key
                return mbr.getMin();
            }
        };
    }

    public int getLeafPageCapacity() {
        int capacity = leafProto.getPageEntryCapacity();
        log.debug("Leaf page capacity: " + capacity);
        return capacity;
    }

}
