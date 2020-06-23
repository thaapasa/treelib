package fi.hut.cs.treelib.concurrency;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Owner;

/**
 * A simple implementation of the latch manager. Uses a global resource lock
 * for synchronizing the accesses to the individual latches.
 * 
 * @author thaapasa
 */
public class DefaultLatchManager implements LatchManager {

    private static final Logger log = Logger.getLogger(DefaultLatchManager.class);

    private Map<LatchTarget, LatchInfo> latches = new HashMap<LatchTarget, LatchInfo>();
    private final Lock globalLock = new ReentrantLock(true);

    public DefaultLatchManager() {
    }

    @Override
    public void readLatch(LatchTarget target, Owner owner) {
        if (log.isDebugEnabled())
            log.debug("Read latch requested for " + target + " by " + owner);
        globalLock.lock();
        try {
            LatchInfo info = getLatchInfo(target, true);
            assert info != null;

            // Will block if needed
            info.readLatch(owner);
        } catch (InterruptedException e) {
            throw new OwnerInterruptedException("writeLatch()", target, owner, e);
        } finally {
            globalLock.unlock();
        }
    }

    @Override
    public void writeLatch(LatchTarget target, Owner owner) {
        if (log.isDebugEnabled())
            log.debug("Write latch requested for " + target + " by " + owner);
        globalLock.lock();
        try {
            LatchInfo info = getLatchInfo(target, true);
            assert info != null;

            // Will block if needed
            info.writeLatch(owner);
        } catch (InterruptedException e) {
            throw new OwnerInterruptedException("writeLatch()", target, owner, e);
        } finally {
            globalLock.unlock();
        }
    }

    @Override
    public void unlatch(LatchTarget target, Owner owner) {
        if (log.isDebugEnabled())
            log.debug("Releasing latch on " + target + " by " + owner);
        globalLock.lock();
        try {
            LatchInfo info = getLatchInfo(target, false);
            assert info != null;

            info.unlatch(owner);

            // Check if the latch info can be removed now
            if (info.isEmpty()) {
                latches.remove(target);
            }
        } finally {
            globalLock.unlock();
        }
    }

    private LatchInfo getLatchInfo(LatchTarget target, boolean create) {
        LatchInfo info = latches.get(target);
        if (info == null && create) {
            info = new LatchInfo();
            latches.put(target, info);
        }
        return info;
    }

    private class LatchInfo {

        private final Lock lock = new ReentrantLock(true);
        private final Condition writer = lock.newCondition();
        private final Condition reader = lock.newCondition();
        private int inWaitQueue = 0;

        private Integer writeLatchHolder = null;
        private Set<Integer> readLatchHolders = new HashSet<Integer>();

        private LatchInfo() {
        }

        public boolean isEmpty() {
            return writeLatchHolder == null && readLatchHolders.isEmpty() && inWaitQueue == 0;
        }

        /**
         * Upon entry, we have both the global lock and the local lock. The
         * global lock is released when we are waiting.
         */
        public void writeLatch(Owner owner) throws InterruptedException {
            lock.lock();
            try {
                if (writeLatchHolder != null && writeLatchHolder.intValue() == owner.getOwnerID()) {
                    log.warn("Write latch holder " + writeLatchHolder
                        + " re-requesting write lock, ignoring");
                    return;
                }
                // Wait until this thread can take the write lock
                waitWrite(owner);

                // At this point we have the local lock and can take the write
                // lock
                assert writeLatchHolder == null : writeLatchHolder;
                assert readLatchHolders.isEmpty() : readLatchHolders;

                writeLatchHolder = owner.getOwnerID();
            } finally {
                lock.unlock();
            }
        }

        public void readLatch(Owner owner) throws InterruptedException {
            lock.lock();
            try {
                // Wait until this thread can acquire a read latch
                waitRead();
                // At this point we have the lock and can take a read latch
                assert writeLatchHolder == null;
                readLatchHolders.add(owner.getOwnerID());
            } finally {
                lock.unlock();
            }
        }

        public void unlatch(Owner owner) {
            lock.lock();
            try {
                if (writeLatchHolder != null) {
                    assert readLatchHolders.isEmpty() : writeLatchHolder + " and "
                        + readLatchHolders;
                    assert writeLatchHolder.intValue() == owner.getOwnerID();
                    // Release write latch
                    writeLatchHolder = null;
                } else {
                    // Release read latch
                    boolean success = readLatchHolders.remove(owner.getOwnerID());
                    assert success;
                }
                signalNext();
            } finally {
                lock.unlock();
            }
        }

        private void waitWrite(Owner owner) throws InterruptedException {
            globalLock.unlock();
            inWaitQueue++;
            try {
                while (true) {
                    // Check if it is ok to take write lock now.
                    // It is okay to take write lock when
                    // a) there is no other write lock AND
                    // b) there are no readers OR there is only one reader who
                    // is requesting upgrade
                    if (writeLatchHolder == null
                        && (readLatchHolders.isEmpty() || (readLatchHolders.size() == 1 && readLatchHolders
                            .contains(owner.getOwnerID())))) {
                        readLatchHolders.clear();
                        break;
                    }
                    // Can't lock now, as there is either a writer or
                    // reader(s)
                    writer.await();
                }
            } finally {
                inWaitQueue--;
                globalLock.lock();
            }
        }

        private void waitRead() throws InterruptedException {
            globalLock.unlock();
            inWaitQueue++;
            try {
                // Can take read latch when there is no other write latch
                while (writeLatchHolder != null) {
                    // Can't take read lock (there is a writer); so wait
                    reader.await();
                }
            } finally {
                inWaitQueue--;
                globalLock.lock();
            }
        }

        private void signalNext() {
            globalLock.unlock();
            // There cannot be a write latch holder at this point, as it would
            // have just been released prior to calling this method
            assert writeLatchHolder == null;

            try {
                if (readLatchHolders.isEmpty()) {
                    // Signal a writer
                    writer.signal();
                }
                if (writeLatchHolder == null) {
                    // No writer in line (or already readers), so signal a
                    // reader
                    reader.signal();
                }
            } finally {
                globalLock.lock();
            }
        }

        @Override
        public String toString() {
            return String.format("Writer: %s, Readers: %s", writeLatchHolder != null ? String
                .valueOf(writeLatchHolder) : "-", readLatchHolders);
        }

    }

}
