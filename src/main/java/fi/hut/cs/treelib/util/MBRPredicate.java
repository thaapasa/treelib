package fi.hut.cs.treelib.util;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;

/**
 * Defaults: if MBR restriction is set, it means that the pages must overlap.
 * 
 * @author thaapasa
 * 
 * @param <K>
 */
public class MBRPredicate<K extends Key<K>> implements Predicate<MBR<K>> {

    private Integer onlyHeight;
    private MBR<K> searchMBR;
    private boolean mbrMustBeContained = false;

    /**
     * True to prefer depth-first search; false to prefer breadth-first
     * search.
     */
    private boolean depthFirst = false;

    public MBRPredicate() {
    }

    public MBRPredicate(MBR<K> mbr, boolean contained, Integer onlyHeight) {
        this.searchMBR = mbr;
        this.mbrMustBeContained = contained;
        this.onlyHeight = onlyHeight;
    }

    /**
     * @param depthFirst true to prefer depth-first search; false to prefer
     * breadth-first search.
     */
    public void setDepthFirst(boolean depthFirst) {
        this.depthFirst = depthFirst;
    }

    public boolean isDepthFirst() {
        return depthFirst;
    }

    public void setSearchMBR(MBR<K> mbr) {
        this.searchMBR = mbr;
    }

    public void setMBRMustBeContaided(boolean value) {
        this.mbrMustBeContained = value;
    }

    public void setOnlyHeight(Integer height) {
        this.onlyHeight = height;
    }

    private boolean rangeMatches(MBR<K> mbr, int height) {
        // Check if the MBR restriction has been set
        if (searchMBR == null)
            return true;
        return mbrMustBeContained ? mbr.contains(searchMBR) : mbr.overlaps(searchMBR);
    }

    @Override
    public boolean matches(MBR<K> mbr, int height) {
        // Check for correct height
        if (onlyHeight != null && height != onlyHeight.intValue())
            return false;
        // Check for range restrictions
        if (!rangeMatches(mbr, height))
            return false;
        return true;
    }

    @Override
    public boolean continueTraversal(MBR<K> mbr, int height) {
        // Check for correct height
        if (onlyHeight != null && height < onlyHeight.intValue())
            return false;
        // Check for range restrictions
        if (!rangeMatches(mbr, height))
            return false;
        return true;
    }

    @Override
    public MBR<K> getRestrictedRange() {
        return searchMBR;
    }

}
