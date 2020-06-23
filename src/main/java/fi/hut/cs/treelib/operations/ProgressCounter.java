package fi.hut.cs.treelib.operations;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Configuration;

public class ProgressCounter {

    private static final Logger log = Logger.getLogger(ProgressCounter.class);

    private String mark;
    private int progressInterval;

    private long totalCount = 0;
    private int progressCounter = 0;

    public ProgressCounter(String mark) {
        this.mark = mark;
        this.progressInterval = Configuration.instance().getShowProgress();
    }

    public long getCount() {
        return totalCount;
    }

    public void advance() {
        totalCount++;

        if (progressInterval > 0) {
            progressCounter++;
            if (progressCounter >= progressInterval) {
                progressCounter = 0;
                log.info(mark + ": " + totalCount);
            }
        }
    }

}
