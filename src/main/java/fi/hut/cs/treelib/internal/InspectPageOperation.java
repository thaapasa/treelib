package fi.hut.cs.treelib.internal;

import java.util.HashSet;
import java.util.Set;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MDPage;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.mdtree.JTree;
import fi.tuska.util.Callback;

public class InspectPageOperation<K extends Key<K>, V extends PageValue<?>> implements
    Callback<Page<K, V>> {

    private static final String LF = "\n";

    private final CountEntriesOperation<K, V> countEntriesOperation;
    private final CountAliveEntriesOperation<K, V> countAliveOperation;
    private final MDRangeCheckOperation<K, V> rangeCheckOperation;
    private final CountVisitedPagesOperation<K, V> countLeafPagesOp;
    private final CountVisitedPagesOperation<K, V> countParentPagesOp;

    private Set<PageID> visited = new HashSet<PageID>();

    public InspectPageOperation(Database<K, V, ?> database) {
        // Standard operations
        this.countEntriesOperation = new CountEntriesOperation<K, V>(database);
        this.countLeafPagesOp = new CountVisitedPagesOperation<K, V>();
        this.countParentPagesOp = new CountVisitedPagesOperation<K, V>();

        // Multiversion operations
        if (database.isMultiVersion()) {
            int version = database.getCommittedVersion();
            this.countAliveOperation = new CountAliveEntriesOperation<K, V>(version);
        } else {
            this.countAliveOperation = null;
        }

        // Multidimensional operations
        if (database.getDatabaseTree() instanceof JTree<?, ?>) {
            this.rangeCheckOperation = new MDRangeCheckOperation<K, V>(database.getDatabaseTree()
                .getHeight(), database.getStatisticsLogger());
        } else {
            this.rangeCheckOperation = null;
        }
    }

    public int getAliveEntryCount() {
        return countAliveOperation.getEntryCount();
    }

    public int getTotalEntryCount() {
        return countEntriesOperation.getEntryCount();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean callback(Page<K, V> page) {
        if (visited.contains(page.getPageID()))
            return true;
        visited.add(page.getPageID());

        if (page.getHeight() == 1) {
            countLeafPagesOp.callback(page);
            countEntriesOperation.callback(page);
            if (countAliveOperation != null) {
                countAliveOperation.callback(page);
            }
        }
        if (page.getHeight() == 2) {
            countParentPagesOp.callback(page);
        }
        if (rangeCheckOperation != null) {
            rangeCheckOperation.callback((MDPage<K, V, ?>) page);
        }
        return true;
    }
    
    public int getLeafPageCount() {
        return countLeafPagesOp.getPageCount();
    }

    public int getParentPageCount() {
        return countParentPagesOp.getPageCount();
    }

    public String getSummary() {
        StringBuilder buf = new StringBuilder();
        buf.append("Leaf pages: ").append(countLeafPagesOp.getPageCount()).append(LF);
        buf.append("Parent pages: ").append(countParentPagesOp.getPageCount()).append(LF);
        if (countAliveOperation != null) {
            buf.append("Entries: ").append(countAliveOperation.getEntryCount());
            buf.append(" alive / ");
            buf.append(countEntriesOperation.getEntryCount()).append(" total").append(LF);
        } else {
            buf.append("Entries: ").append(countEntriesOperation.getEntryCount()).append(LF);
        }

        if (rangeCheckOperation != null) {
            buf.append(rangeCheckOperation.getSummary()).append(LF);
        }
        return buf.toString().trim();
    }
}
