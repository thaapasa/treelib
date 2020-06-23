package fi.hut.cs.treelib.util;

/**
 * Predicates are used in tree traversals to control which pages and which
 * children are traversed. A Predicate can be constructed by using the static
 * methods from the Predicates class.
 * 
 * The predicates normally take a key range, key or an MBR that specify what
 * is being looked for. Tree traversal then checks if a page matches by
 * calling matches() with the page key range as parameter. Note that for
 * R-trees, the key range of MBR's is not used as a key range; the MBR is just
 * embedded as the min key of the key range. On the other hand, J-trees do use
 * MBR key ranges (J-tree orders MBRs based on the individual key values).
 * 
 * @author tuska
 * 
 * @param <K>
 */
public interface Predicate<K> {

    /**
     * Does this predicate match the given page key range?
     * 
     * @param range the page's key range (or the page MBR wrapped in a
     * pseudo-key-range for R-tree pages)
     * @param height the page's height (some predicates work differently on
     * leaf pages compared to index pages)
     * @return true if the predicate matches; false otherwise
     */
    boolean matches(K range, int height);

    /**
     * Does this predicate match the given page key range?
     * 
     * @param range the page's key range (or the page MBR wrapped in a
     * pseudo-key-range for R-tree pages)
     * @param height the page's height (some predicates work differently on
     * leaf pages compared to index pages)
     * @return true if the predicate matches; false otherwise
     */
    boolean continueTraversal(K range, int height);

    K getRestrictedRange();

}
