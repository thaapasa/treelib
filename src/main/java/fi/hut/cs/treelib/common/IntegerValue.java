package fi.hut.cs.treelib.common;

import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;

public class IntegerValue extends AbstractIntegerValue<IntegerValue> implements
    PageValue<Integer> {

    public static final IntegerValue PROTOTYPE = new IntegerValue();

    private IntegerValue() {
        super(0);
    }

    public IntegerValue(Integer value) {
        super(value);
    }

    public IntegerValue(PageID pageID) {
        super(pageID.getValue());
    }

    @Override
    public Integer getValue() {
        return intValue;
    }

    @Override
    protected IntegerValue instantiate(Integer value) {
        return new IntegerValue(value);
    }

}
