package fi.hut.cs.treelib;

import fi.hut.cs.treelib.common.AbstractIntegerValue;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.concurrency.LatchTarget;

/**
 * The page ID is immutable, so you can safely share instances.
 * 
 * @author thaapasa
 */
public class PageID extends AbstractIntegerValue<PageID> implements PageValue<Integer>,
    LatchTarget {

    public static final PageID INVALID_PAGE_ID = new PageID();
    public static final PageID PROTOTYPE = new PageID();

    /** Creates an invalid page id. */
    private PageID() {
        super(null);
        assert !isValid();
    }

    public PageID(int pageID) {
        super(pageID);
        assert isValid();
    }

    public PageID(IntegerValue value) {
        this(value.getValue());
    }

    @Override
    public Integer getValue() {
        return intValue;
    }

    @Override
    public String toString() {
        return "PageID " + intValue;
    }

    @Override
    protected PageID instantiate(Integer value) {
        return value != null ? new PageID(value) : new PageID();
    }

}
