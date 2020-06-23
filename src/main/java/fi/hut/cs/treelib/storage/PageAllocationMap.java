package fi.hut.cs.treelib.storage;

import java.nio.ByteBuffer;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.util.ByteUtils;

public class PageAllocationMap extends AbstractPage implements Component {

    public static final int PAGE_IDENTIFIER = 0xA110CA7E;

    private ByteBuffer freePageData;
    private static final byte[] ONE_BIT_COUNTS;

    static {
        ONE_BIT_COUNTS = new byte[256];
        for (int i = 0; i < 256; i++) {
            ONE_BIT_COUNTS[i] = countOneBits((byte) i);
        }
    }

    public static byte countOneBits(byte b) {
        byte bits = 0;
        int mask = 1;
        while (mask < 256) {
            if ((b & mask) != 0)
                bits++;
            mask <<= 1;
        }
        return bits;
    }

    public PageAllocationMap(PageID pageID, int pageSize) {
        super(pageID, pageSize);
    }

    public boolean isFree(PageID pageID) {
        int index = toBitIndex(pageID);
        return !ByteUtils.getBit(freePageData, index);
    }

    public PageID getMinPageID() {
        return getPageID();
    }

    public PageID getMaxPageID() {
        return new PageID(getPageID().intValue() + freePageData.capacity() * 8 - 1);
    }

    private int getMapBytes() {
        return freePageData.capacity();
    }

    /**
     * Finds a free page id and reserves it.
     * 
     * @return the page id that was free, but is now reserved; or null if the
     * allocation map is full.
     */
    public PageID findAndReserve() {
        for (int i = 0; i < getMapBytes(); i++) {
            if ((freePageData.get(i) & 0xff) != 255) {
                // There is free space in this byte
                for (int b = 0; b < 8; b++) {
                    int mask = 1 << b;
                    if (((freePageData.get(i) & 0xff) & mask) == 0) {
                        PageID pageID = new PageID(getPageID().intValue() + 8 * i + b);
                        // Reserve sets the page dirty
                        reserve(pageID);
                        return pageID;
                    }
                }
            }
        }
        // No free space found
        return null;
    }

    public void release(PageID pageID) {
        int index = toBitIndex(pageID);
        if (!ByteUtils.getBit(freePageData, index))
            throw new IllegalStateException("Trying to release a free page " + pageID);
        ByteUtils.clearBit(freePageData, index);
        setDirty(true);
    }

    public void reserve(PageID pageID) {
        int index = toBitIndex(pageID);
        if (ByteUtils.getBit(freePageData, index))
            throw new IllegalStateException("Trying to reserve a non-free page " + pageID);
        ByteUtils.setBit(freePageData, index);
        setDirty(true);
    }

    @Override
    protected boolean loadPageDataImpl(ByteBuffer pageData) {
        int pageType = pageData.getInt();
        if (pageType != getTypeIdentifier())
            return false;

        freePageData = pageData.slice();
        assert freePageData != null;
        assert freePageData.capacity() == pageData.capacity() - 4;

        if (isFree(getPageID())) {
            // Loaded before creation...
            reserve(getPageID());
        }
        return true;
    }

    @Override
    protected void savePageDataImpl(ByteBuffer pageData) {
        // Already saved
    }

    @Override
    public void formatNewPage(ByteBuffer pageData) {
        super.formatNewPage(pageData);
        pageData.rewind();
        pageData.putInt(getTypeIdentifier());

        freePageData = pageData.slice();
        assert freePageData != null;

        // Check that this page is reserved in the space map
        reserve(getPageID());
    }

    private int toBitIndex(PageID pageID) {
        return pageID.intValue() - getPageID().intValue();
    }

    /**
     * @return the amount of PageIDs this allocation map manages
     */
    public int getPageCapacity() {
        return getMapBytes() * 8;
    }

    @Override
    public String toString() {
        return String.format(
            "Page allocation map (id %d), size: %d bytes, %d/%d pages: %d-%d (%d bytes)",
            getPageID().intValue(), getPageSize(), getPageCount(), getPageCapacity(),
            getMinPageID().intValue(), getMaxPageID().intValue(), getMapBytes());
    }

    public int getPageCount() {
        int pages = 0;
        for (int i = 0; i < getMapBytes(); i++) {
            pages += ONE_BIT_COUNTS[freePageData.get(i) & 0xff];
        }
        return pages;
    }

    private static void toBitString(byte value, StringBuilder b) {
        for (int i = 0; i < 8; i++) {
            int mask = 1 << i;
            b.append((((value & 0xff) & mask) == 0) ? "0" : "1");
        }
    }

    public String getAllocationStatus(PageID startPageID, PageID endPageID) {
        int minID = toBitIndex(startPageID);
        int maxID = toBitIndex(endPageID);
        minID -= minID % 8;
        maxID -= maxID % 8;
        StringBuilder b = new StringBuilder();
        b.append("[").append(minID + getPageID().intValue()).append("]");

        int minIndex = minID / 8;
        int maxIndex = maxID / 8;
        for (int i = minIndex; i <= maxIndex; i++) {
            b.append(" ");
            toBitString(freePageData.get(i), b);
        }
        b.append(" [").append(maxID + getPageID().intValue() + 7).append("]");
        return b.toString();
    }

    @Override
    public void checkConsistency(Object... params) {
    }

    @Override
    public void printDebugInfo() {
        System.out.print("Reserved pages: ");
        int maxPages = getMapBytes() * 8;
        boolean first = true;
        for (int i = 0; i < maxPages; i++) {
            if (ByteUtils.getBit(freePageData, i)) {
                if (first) {
                    first = false;
                } else {
                    System.out.print(", ");
                }
                int curID = getPageID().intValue() + i;
                System.out.print(curID);
            }
        }
        System.out.println();
    }

    @Override
    public int getTypeIdentifier() {
        return PAGE_IDENTIFIER;
    }

}
