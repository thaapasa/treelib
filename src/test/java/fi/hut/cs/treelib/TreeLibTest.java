package fi.hut.cs.treelib;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.tuska.util.AssertionSupport;
import junit.framework.TestCase;

public abstract class TreeLibTest extends TestCase {

    protected static final Owner TEST_OWNER = new OwnerImpl("TestCase");
    protected double tolerance = 0.001;

    protected static final Logger log = Logger.getLogger(TreeLibTest.class);

    protected IntegerKey getIK(int key) {
        return new IntegerKey(key);
    }

    protected void showTestName() {
        if (log.isInfoEnabled()) {
            StackTraceElement[] traces = new Exception().getStackTrace();
            log.info(String.format("Start test: %s, assertions: %s", traces[1], AssertionSupport
                .isAssertionsEnabled()));
        }
    }

    protected void pause(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
        }
    }

    protected void assertAboutSame(double a, double b) {
        double dist = Math.abs(a - b);
        if (dist > tolerance) {
            fail(a + " != " + b);
        }
    }

    /**
     * Asserts that a < b.
     */
    protected void assertLT(int a, int b) {
        assertTrue("<" + a + "> not less than <" + b + ">", a < b);
    }

    /**
     * Asserts that a <= b.
     */
    protected void assertLTE(int a, int b) {
        assertTrue("<" + a + "> not less than or equal to <" + b + ">", a <= b);
    }

    /**
     * Asserts that a > b.
     */
    protected void assertGT(int a, int b) {
        assertTrue("<" + a + "> not greater than <" + b + ">", a > b);
    }

    /**
     * Asserts that a >= b.
     */
    protected void assertGTE(int a, int b) {
        assertTrue("<" + a + "> not greater than or equal to <" + b + ">", a >= b);
    }

    /**
     * Asserts that a < b.
     */
    protected void assertLT(float a, float b) {
        assertTrue("<" + a + "> not less than <" + b + ">", a < b);
    }

    /**
     * Asserts that a <= b.
     */
    protected void assertLTE(float a, float b) {
        assertTrue("<" + a + "> not less than or equal to <" + b + ">", a <= b);
    }

    /**
     * Asserts that a > b.
     */
    protected void assertGT(float a, float b) {
        assertTrue("<" + a + "> not greater than <" + b + ">", a > b);
    }

    /**
     * Asserts that a >= b.
     */
    protected void assertGTE(float a, float b) {
        assertTrue("<" + a + "> not greater than or equal to <" + b + ">", a >= b);
    }

    /**
     * Asserts that a < b, that is, a is ordered before b. Uses the
     * compareTo() method to test this.
     */
    protected <T> void assertBefore(Comparable<T> a, T b) {
        int comp = a.compareTo(b);
        assertTrue("<" + a + "> not before <" + b + ">, comparison returned: " + comp, comp < 0);
    }

}
