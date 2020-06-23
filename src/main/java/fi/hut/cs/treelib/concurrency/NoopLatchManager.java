package fi.hut.cs.treelib.concurrency;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Owner;

/**
 * Dummy implementation of the latch manager. Only logs the latch requests,
 * for debugging.
 * 
 * @author thaapasa
 */
public class NoopLatchManager implements LatchManager {

    private static final Logger log = Logger.getLogger(NoopLatchManager.class);

    private static NoopLatchManager instance;

    private NoopLatchManager() {
    }

    public static LatchManager instance() {
        if (instance == null) {
            instance = new NoopLatchManager();
        }
        assert instance != null;
        return instance;
    }

    @Override
    public void readLatch(LatchTarget target, Owner owner) {
        if (log.isDebugEnabled())
            log.debug("Read latch requested to " + target + " by " + owner);
    }

    @Override
    public void writeLatch(LatchTarget target, Owner owner) {
        if (log.isDebugEnabled())
            log.debug("Write latch requested to " + target + " by " + owner);
    }

    @Override
    public void unlatch(LatchTarget target, Owner owner) {
        if (log.isDebugEnabled())
            log.debug("Latch of " + target + " released by " + owner);
    }

}
