package fi.hut.cs.treelib.simulator;

/**
 * Interface for simulator programs that operate on the database and that can
 * be run.
 * 
 * @author thaapasa
 */
public interface Program extends Runnable {

    /**
     * Starts and runs the program.
     */
    void run();

}
