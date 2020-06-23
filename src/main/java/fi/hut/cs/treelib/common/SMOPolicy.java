package fi.hut.cs.treelib.common;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;

public interface SMOPolicy {

    public double getMinFillRate();

    /**
     * @return the minimum number of entries on the page before an SMO is
     * triggered
     */
    public int getMinEntries(Page<?, ?> page);

    /**
     * @return the maximum number of entries on the page before an SMO is
     * triggered
     */
    public int getMaxEntries(Page<?, ?> page);

    /**
     * @return true if a deletion will trigger an underflow on the page
     */
    public <K extends Key<K>, V extends PageValue<?>> boolean isAboutToUnderflow(Page<K, V> page,
        PagePath<K, V, ? extends Page<K, V>> path);

    /**
     * @return true if an insertion will trigger an overflow on the page
     */
    public boolean isAboutToOverflow(Page<?, ?> page);

    /**
     * @return the minimum allowed number of entries on the page after an SMO
     */
    public int getMinEntriesAfterSMO(Page<?, ?> page);

    /**
     * @return the maximum allowed number of entries on the page after an SMO
     */
    public int getMaxEntriesAfterSMO(Page<?, ?> page);

}
