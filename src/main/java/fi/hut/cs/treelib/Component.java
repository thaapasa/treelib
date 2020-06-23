package fi.hut.cs.treelib;

/**
 * Idea: classes that implement this interface can be automatically used by
 * various other components.
 * 
 * Current implementation: classes that implement Component can be examined
 * (their debug information can be printed).
 * 
 * @author thaapasa
 */
public interface Component {

    void printDebugInfo();

    void checkConsistency(Object... params);

}
