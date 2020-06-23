package fi.hut.cs.treelib.concurrency;

import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.TreeLibTest;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.tuska.util.Holder;

public class DefaultLatchManagerTest extends TreeLibTest {

    private LatchManager mgr;

    private static final PageID P1 = new PageID(1);
    private static final PageID P2 = new PageID(2);
    private static final PageID P3 = new PageID(3);

    private static final Owner O1 = new OwnerImpl("O1");
    private static final Owner O2 = new OwnerImpl("O2");
    private static final Owner O3 = new OwnerImpl("O3");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mgr = new DefaultLatchManager();
    }

    public void testLatches() {
        mgr.readLatch(P1, O1);
        mgr.readLatch(P1, O2);
        mgr.readLatch(P2, O1);
        mgr.readLatch(P2, O3);
        mgr.writeLatch(P3, O1);

        mgr.unlatch(P1, O1);
        mgr.unlatch(P1, O2);
        mgr.writeLatch(P1, O3);
    }

    protected void spawnWrite(final LatchTarget target, final Owner owner,
        final Holder<Boolean> notification) {
        // Start thread 1
        new Thread(new Runnable() {
            @Override
            public void run() {
                // This can block
                mgr.writeLatch(target, owner);
                notification.setValue(true);
            }
        }).start();
    }

    protected void spawnRead(final LatchTarget target, final Owner owner,
        final Holder<Boolean> notification) {
        // Start thread 1
        new Thread(new Runnable() {
            @Override
            public void run() {
                // This can block
                mgr.readLatch(target, owner);
                notification.setValue(true);
            }
        }).start();
    }

    public void testReadersBlockWrite() {
        final Holder<Boolean> notification = new Holder<Boolean>(false);
        mgr.readLatch(P1, O1);
        mgr.readLatch(P1, O2);
        // Start a writer
        spawnWrite(P1, O3, notification);

        // Must not continue until both latches released
        assertFalse(notification.getValue());
        pause(0.3);
        assertFalse(notification.getValue());
        mgr.unlatch(P1, O1);
        assertFalse(notification.getValue());
        pause(0.3);
        assertFalse(notification.getValue());
        mgr.unlatch(P1, O2);
        pause(0.3);
        while (!notification.getValue()) {
            // Wait until the spawned thread gets the write latch and sets the
            // notification
            Thread.yield();
        }

        notification.setValue(false);
        // Situation: (owner) O3 holds write latch on (page) P1
        spawnRead(P1, O1, notification);
        assertFalse(notification.getValue());
        pause(0.3);
        assertFalse(notification.getValue());
        // Release the write latch of O3
        mgr.unlatch(P1, O3);
        pause(0.3);
        // Now O1 can continue to take the read latch
        assertTrue(notification.getValue());
        // And this should work again also
        mgr.readLatch(P1, O2);
    }

}
