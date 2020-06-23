package fi.hut.cs.treelib.test;

import fi.hut.cs.treelib.Key;

public class KeyBounds {

    private String bounds;
    private String minLimit;
    private String maxLimit;

    public KeyBounds() {
    }

    public void setBounds(String bounds) {
        this.bounds = bounds;
    }

    public void setMinLimit(String minLimit) {
        this.minLimit = minLimit;
    }

    public void setMaxLimit(String maxLimit) {
        this.maxLimit = maxLimit;
    }

    public <K extends Key<K>> K getMinLimit(K proto) {
        if (minLimit == null)
            return proto.getMinKey();
        else
            return proto.parse(minLimit);
    }

    public <K extends Key<K>> K getMaxLimit(K proto) {
        if (maxLimit == null)
            return proto.getMaxKey();
        else
            return proto.parse(maxLimit);
    }

    public <K extends Key<K>> K getBounds(K proto) {
        if (bounds == null)
            return proto.getMinKey();
        else
            return proto.parse(bounds);
    }

    public <K extends Key<K>> K getRangeSize(K proto, double size) {
        K max = getMaxLimit(proto);
        K min = getMinLimit(proto);
        return max.subtract(min).multiply(size);
    }

}
