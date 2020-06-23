package fi.hut.cs.treelib.common;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.internal.KeyRangeImpl;

public class IntegerKeyRange extends KeyRangeImpl<IntegerKey> {

    private int min;
    private int max;

    public IntegerKeyRange(KeyRange<IntegerKey> range) {
        super(range.getMin(), range.getMax());
        this.min = range.getMin().intValue();
        this.max = range.getMax().intValue();
    }

    public IntegerKeyRange(int min, int max) {
        super(new IntegerKey(min), new IntegerKey(max));
        this.min = min;
        this.max = max;
    }

    public IntegerKeyRange(IntegerKey min, IntegerKey max) {
        super(min, max);
        this.min = min.intValue();
        this.max = max.intValue();
    }

    public static IntegerKeyRange convert(KeyRange<IntegerKey> range) {
        if (range instanceof IntegerKeyRange)
            return (IntegerKeyRange) range;
        return new IntegerKeyRange(range);
    }

    @Override
    protected KeyRange<IntegerKey> instantiate(IntegerKey min, IntegerKey max) {
        return new IntegerKeyRange(min, max);
    }

    @Override
    protected KeyRange<IntegerKey> instantiate(IntegerKey onlyKey) {
        return new IntegerKeyRange(onlyKey, onlyKey);
    }

    public boolean contains(int key) {
        return min <= key && key < max;
    }

    @Override
    public IntegerKeyRange readFromBytes(ByteBuffer byteArray) {
        int min = byteArray.getInt();
        int max = byteArray.getInt();
        return new IntegerKeyRange(min, max);
    }

}
