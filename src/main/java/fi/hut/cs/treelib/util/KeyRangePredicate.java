package fi.hut.cs.treelib.util;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;

public class KeyRangePredicate<K extends Key<K>> implements Predicate<KeyRange<K>> {

    private Integer onlyHeight;
    private KeyRange<K> restrictedRange;

    public KeyRangePredicate() {
    }

    public void setRestrictedRange(KeyRange<K> range) {
        this.restrictedRange = range;
    }

    public void setOnlyHeight(Integer height) {
        this.onlyHeight = height;
    }

    @Override
    public boolean matches(KeyRange<K> range, int height) {
        // Check for correct height
        if (onlyHeight != null && height != onlyHeight.intValue())
            return false;
        // Check for range restrictions
        if (restrictedRange != null && !range.intersects(restrictedRange))
            return false;
        return true;
    }

    @Override
    public boolean continueTraversal(KeyRange<K> range, int height) {
        // Check for correct height
        if (onlyHeight != null && height < onlyHeight.intValue())
            return false;
        // Check for range restrictions
        if (restrictedRange != null && !range.intersects(restrictedRange))
            return false;
        return true;
    }

    @Override
    public KeyRange<K> getRestrictedRange() {
        return restrictedRange;
    }

}
