package fi.hut.cs.treelib.util;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.MVKeyRange;
import fi.hut.cs.treelib.common.IntegerKey;

public class MVKeyRangePredicate<K extends Key<K>> implements Predicate<MVKeyRange<K>> {

    private final K proto;

    private Integer onlyHeight;
    private KeyRange<K> restrictedKeyRange;
    private KeyRange<IntegerKey> restrictedVersionRange;

    public MVKeyRangePredicate(K proto) {
        this.proto = proto;
    }

    public MVKeyRangePredicate(K proto, Predicate<KeyRange<K>> pred) {
        this(proto);
        KeyRange<K> keyRange = pred.getRestrictedRange();
        setRestrictedRange(keyRange);
    }

    public void setRestrictedMVRange(MVKeyRange<K> range) {
        this.restrictedKeyRange = range;
        this.restrictedVersionRange = range.getVersionRange();
    }

    public void setRestrictedRange(KeyRange<K> range) {
        this.restrictedKeyRange = range;
    }

    public void setRestrictedVersionRange(KeyRange<IntegerKey> range) {
        this.restrictedVersionRange = range;
    }

    public void setOnlyHeight(Integer height) {
        this.onlyHeight = height;
    }

    private boolean rangeMatches(MVKeyRange<K> range) {
        if (restrictedKeyRange != null) {
            // Check that key range is correct
            if (!restrictedKeyRange.intersects(range))
                return false;
        }
        if (restrictedVersionRange != null) {
            // Check that version range is correct
            if (!restrictedVersionRange.intersects(range.getVersionRange()))
                return false;
        }
        return true;
    }

    @Override
    public boolean matches(MVKeyRange<K> range, int height) {
        // Check for correct height
        if (onlyHeight != null && height != onlyHeight.intValue())
            return false;
        // Check for range restrictions
        if (!rangeMatches(range))
            return false;
        return true;
    }

    @Override
    public boolean continueTraversal(MVKeyRange<K> range, int height) {
        // Check for correct height
        if (onlyHeight != null && height < onlyHeight.intValue())
            return false;
        // Check for range restrictions
        if (!rangeMatches(range))
            return false;
        return true;
    }

    @Override
    public MVKeyRange<K> getRestrictedRange() {
        KeyRange<K> kr = restrictedKeyRange != null ? restrictedKeyRange : proto.getEntireRange();
        KeyRange<IntegerKey> vr = restrictedVersionRange != null ? restrictedVersionRange
            : IntegerKey.PROTOTYPE.getEntireRange();
        return new MVKeyRange<K>(kr, vr);
    }

}
