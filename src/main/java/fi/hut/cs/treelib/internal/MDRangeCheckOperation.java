package fi.hut.cs.treelib.internal;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDPage;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.tuska.util.Array;
import fi.tuska.util.Callback;

public class MDRangeCheckOperation<K extends Key<K>, V extends PageValue<?>> extends
    PageCheckOperation<MBR<K>, V> implements Callback<Page<MBR<K>, V>> {

    private static final Logger log = Logger.getLogger(MDRangeCheckOperation.class);

    private final Array<MDPage<K, V, ?>> lastPageA;
    private final Array<MBR<K>> lastMBRA;

    private final int[] sameXPages;
    private final int[] wrongMaxXOrderPages;
    private final int[] totalPages;
    private final boolean[] lastMatched;
    private final int maxHeight;

    private int cumulatedSameMinPoints;
    private int totalSameMinPoints;
    private int maxSameMinPoints;
    private MBR<K> lastEntryMBR;

    public MDRangeCheckOperation(int maxHeight, StatisticsLogger stats) {
        super(stats);
        this.maxHeight = maxHeight;
        this.wrongMaxXOrderPages = new int[maxHeight + 1];
        this.totalPages = new int[maxHeight + 1];
        this.sameXPages = new int[maxHeight + 1];
        this.lastMBRA = new Array<MBR<K>>(maxHeight + 1);
        this.lastPageA = new Array<MDPage<K, V, ?>>(maxHeight + 1);
        this.lastMatched = new boolean[maxHeight + 1];
    }

    private void processNextEntry(MBR<K> curMBR) {
        if (lastEntryMBR != null && curMBR.getLow(0).equals(lastEntryMBR.getLow(0))
            && curMBR.getLow(1).equals(lastEntryMBR.getLow(1))) {
            // Same X,Y for two consecutive entries
            if (cumulatedSameMinPoints == 0) {
                // Count the previous one, as it was not counted
                // when it was encountered
                cumulatedSameMinPoints++;
                totalSameMinPoints++;
            }
            cumulatedSameMinPoints++;
            totalSameMinPoints++;
            maxSameMinPoints = Math.max(maxSameMinPoints, cumulatedSameMinPoints);
        } else {
            cumulatedSameMinPoints = 0;
        }
        lastEntryMBR = curMBR;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean callback(Page<MBR<K>, V> page) {
        if (!super.callback(page))
            return false;
        if (!(page instanceof MDPage))
            throw new UnsupportedOperationException(
                "Range check not supported for non-multidimensional pages");

        int height = page.getHeight();
        if (height > maxHeight)
            throw new IllegalArgumentException("Too high page, height " + height + ": " + page);

        totalPages[height]++;

        MDPage<K, V, ?> mvPage = (MDPage<K, V, ?>) page;

        // Check that entries in page have different min keys
        // mvPage.getKeys();

        if (height > 2)
            return true;

        MBR<K> curMBR = null;
        MDPage<K, V, ?> lastPage = lastPageA.get(height);
        MBR<K> lastMBR = lastMBRA.get(height);
        if (lastPage != null) {
            curMBR = mvPage.getPageMBR();
            assert curMBR.getLow(0).compareTo(lastMBR.getLow(0)) >= 0 : curMBR.getLow(0) + " < "
                + lastMBR.getLow(0) + "; " + mvPage;
            // Check if min-Xs are the same
            if (curMBR.getLow(0).equals(lastMBR.getLow(0))) {

                for (MBR<K> key : lastPage.getKeys()) {
                    if (!key.getLow(0).equals(curMBR.getLow(0)))
                        throw new IllegalStateException("Page " + lastPage + " contains key "
                            + key + " which is not X-min=" + curMBR.getLow(0));
                }
                String msg = String.format("Same: X at pages %s and %s at height %d: %s and %s",
                    lastPage.getPageID(), page.getPageID(), height, lastMBR, curMBR);
                if (sameXPages[height] < 20) {
                    log.warn(msg);
                } else {
                    log.info(msg);
                }

                sameXPages[height]++;
                if (!lastMatched[height]) {
                    sameXPages[height]++;
                    lastMatched[height] = true;
                }

            } else {
                lastMatched[height] = false;
            }
            // Check if the max-X of the last page is bigger than the max-X of
            // this page
            if (lastMBR.getHigh(0).compareTo(curMBR.getHigh(0)) > 0) {
                String msg = String.format(
                    "Wrong max-X order at %d: %s (%s) and %s (%s) (current)", height, lastMBR,
                    lastPage.getPageID(), curMBR, page.getPageID());
                if (wrongMaxXOrderPages[height] < 4) {
                    log.warn(msg);
                } else {
                    log.info(msg);
                }
                wrongMaxXOrderPages[height]++;
            }
        }
        if (height == 1) {
            for (MBR<K> key : mvPage.getKeys()) {
                processNextEntry(key);
            }
        }
        lastPageA.put(height, mvPage);
        lastMBRA.put(height, curMBR != null ? curMBR : mvPage.getPageMBR());
        return true;
    }

    @Override
    public String getSummary() {
        StringBuilder buf = new StringBuilder();
        for (int i = maxHeight; i > 0; i--) {
            int entries = getTotalPageCount(i);
            int wrongX = getWrongXMaxOrderPageCount(i);
            buf.append("Check result for height ").append(i).append(" (entries: ")
                .append(entries).append(")\n");
            buf.append("Same X pages: ").append(getSameXPageCount(i)).append("\n");
            buf.append(String.format("Wrong X-max order pages: %d (%.2f %%)\n", wrongX,
                entries > 0 ? wrongX * 100.0 / entries : 0.0));
        }
        buf.append("Consecutive same min X,Y values: ").append(getTotalSameMinPoints()).append(
            " (max ").append(getMaxSameMinPoints()).append(" consecutive)\n");

        return buf.toString().trim();
    }

    public int getSameXPageCount(int height) {
        if (height > maxHeight)
            throw new IllegalArgumentException("Height out of bounds: " + height + ", max is "
                + maxHeight);
        return sameXPages[height];
    }

    public int getWrongXMaxOrderPageCount(int height) {
        if (height > maxHeight)
            throw new IllegalArgumentException("Height out of bounds: " + height + ", max is "
                + maxHeight);
        return wrongMaxXOrderPages[height];
    }

    public int getTotalPageCount(int height) {
        if (height > maxHeight)
            throw new IllegalArgumentException("Height out of bounds: " + height + ", max is "
                + maxHeight);
        return totalPages[height];
    }

    public int getTotalSameMinPoints() {
        return totalSameMinPoints;
    }

    public int getMaxSameMinPoints() {
        return maxSameMinPoints;
    }

}
