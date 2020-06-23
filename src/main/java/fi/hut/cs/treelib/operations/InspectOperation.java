package fi.hut.cs.treelib.operations;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.internal.InspectPageOperation;
import fi.hut.cs.treelib.util.KeyRangePredicate;
import fi.hut.cs.treelib.util.MBRPredicate;

public class InspectOperation<K extends Key<K>, V extends PageValue<?>> implements Operation<K> {

    private static final Logger log = Logger.getLogger(InspectOperation.class);

    private Database<K, V, ?> database;
    protected final K keyPrototype;
    private final Owner owner = new OwnerImpl("inspect");

    public InspectOperation(Database<K, V, ?> database) {
        this.database = database;
        this.keyPrototype = database.getDatabaseTree().getKeyPrototype();
    }

    private void inspectPage(Page<K, V> page) {
        System.out.println("Page: " + page);
        if (page == null)
            return;
        System.out.println("Page height: " + page.getHeight() + " (page is "
            + (page.isLeafPage() ? "" : "not ") + "a leaf page)");
        System.out.println("Page contents: " + page.getEntryCount() + "/"
            + page.getPageEntryCapacity() + " entries, page size " + page.getPageSize()
            + " bytes");

        // System.out.println("Contents:");
    }

    @SuppressWarnings("unchecked")
    protected void inspectDB() {
        Tree<K, V, ?> tree = database.getDatabaseTree();

        System.out.println(database);
        System.out.println("Last page ID: "
            + tree.getPageBuffer().getPageStorage().getMaxPageID());
        System.out.println("Total page count: " + tree.getPageBuffer().getTotalPageCount());

        String summary = null;
        if (database instanceof MDDatabase) {
            summary = inspectMDDatabase((MDDatabase<MBR<K>, IntegerValue, ?>) database);
        } else {

            InspectPageOperation<K, V> op = new InspectPageOperation<K, V>(database);
            database.traversePages(new KeyRangePredicate<K>(), op, owner);
            summary = op.getSummary();
        }
        System.out.println(summary);
    }

    protected <T extends Key<T>> String inspectMDDatabase(MDDatabase<T, IntegerValue, ?> db) {
        InspectPageOperation<MBR<T>, IntegerValue> op = new InspectPageOperation<MBR<T>, IntegerValue>(
            db);

        MBRPredicate<T> pred = new MBRPredicate<T>();
        pred.setDepthFirst(true);
        db.traverseMDPages(pred, op, owner);
        return op.getSummary();
    }

    @Override
    public void execute(Iterable<K> keys) {
        log.info("Executing inspect...");

        inspectDB();
        Tree<K, V, ?> tree = database.getDatabaseTree();
        Page<K, V> root = tree.getRoot(owner);

        System.out.println("Root page:");
        inspectPage(root);

        if (root != null && !root.isLeafPage()) {
            Page<K, V> minPage = tree.getPage(keyPrototype.getMinKey(), owner);
            if (minPage != null) {
                assert minPage.isLeafPage();
                System.out.println("Page that contains min key:");
                inspectPage(minPage);
                tree.getPageBuffer().unfix(minPage, owner);
            }

            Page<K, V> maxPage = tree.getPage(keyPrototype.getMaxKey().previousKey(), owner);
            if (maxPage != null) {
                assert maxPage.isLeafPage();
                System.out.println("Page that contains max key:");
                inspectPage(maxPage);
                tree.getPageBuffer().unfix(maxPage, owner);
            }
        }

        tree.getPageBuffer().unfix(root, owner);

        System.out.println("Buffer fixes: " + tree.getPageBuffer().getTotalPageFixes());
        System.out.println("Buffer fix summary: " + tree.getPageBuffer().getPageFixSummary());

        // Disable statistics showing for inspect
        Configuration.instance().setShowStatistics(false);

        log.info("Printing debug info...");
        database.printDebugInfo();
    }

    @Override
    public boolean requiresKeys() {
        return false;
    }
}
