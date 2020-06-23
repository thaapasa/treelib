package fi.hut.cs.treelib.data;

import fi.hut.cs.treelib.common.IntegerValue;

public class IntegerValueGenerator implements Generator<IntegerValue> {

    private int lastGenerated = 0;

    @Override
    public IntegerValue generate() {
        return new IntegerValue(++lastGenerated);
    }

    @Override
    public IntegerValue getPrototype() {
        return IntegerValue.PROTOTYPE;
    }

}
