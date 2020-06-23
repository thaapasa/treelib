package fi.hut.cs.treelib.concurrency;

import fi.hut.cs.treelib.Owner;

/**
 * Interface for the latch manager.
 * 
 * @author tuska
 */
public interface LatchManager {

    /**
     * Read-latches the latchable target object.
     */
    void readLatch(LatchTarget target, Owner owner);

    /**
     * Write-latches the latchable target object.
     */
    void writeLatch(LatchTarget target, Owner owner);

    /**
     * Unlatches the target latchable object.
     */
    void unlatch(LatchTarget target, Owner owner);

}
