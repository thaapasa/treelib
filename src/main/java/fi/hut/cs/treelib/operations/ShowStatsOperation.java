package fi.hut.cs.treelib.operations;

import java.io.PrintStream;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.internal.InspectPageOperation;
import fi.hut.cs.treelib.mvbt.CMVBTDatabase;
import fi.hut.cs.treelib.mvbt.VersionedBTree;
import fi.hut.cs.treelib.util.KeyRangePredicate;
import fi.tuska.util.NotImplementedException;

public class ShowStatsOperation<K extends Key<K>, V extends PageValue<?>> implements Operation<K> {

    private static final Logger log = Logger.getLogger(ShowStatsOperation.class);

    private static final PrintStream target = System.err;

    private Database<K, V, ?> database;
    protected final K keyPrototype;
    private final Owner owner = new OwnerImpl("inspect");

    public ShowStatsOperation(Database<K, V, ?> database) {
        this.database = database;
        this.keyPrototype = database.getDatabaseTree().getKeyPrototype();
    }

    protected void inspectDB() {
        Tree<K, V, ?> tree = database.getDatabaseTree();

        if (database instanceof MDDatabase<?, ?, ?>) {
            throw new NotImplementedException();
        } else {
            InspectPageOperation<K, V> op = new InspectPageOperation<K, V>(database);
            database.traversePages(new KeyRangePredicate<K>(), op, owner);
            int leaf = op.getLeafPageCount();
            int parent = op.getParentPageCount();
            int pagesInFile = tree.getPageBuffer().getTotalPageCount();
            target.println("Entries (alive/all): " + op.getAliveEntryCount() + " / "
                + op.getTotalEntryCount());
            target.println("Pages (leaf/parent/total/file): " + leaf + " / " + parent + " / "
                + (leaf + parent) + " / " + pagesInFile);
        }
    }

    private void inspectPage(Page<?, ?> page) {
        if (page == null)
            return;
        target.print(page.isLeafPage() ? "Leaf page: " : "Index page: ");
        target.print(page.getPageEntryCapacity() + " entries");
        if (!page.isLeafPage()) {
            target.print("; height: " + page.getHeight());
        }
        target.println();
    }

    private <KK extends Key<KK>, VV extends PageValue<?>> void describeTree(Tree<KK, VV, ?> tree) {
        target.println("Tree: " + tree.getIdentifier());
        Page<?, ?> root = tree.getRoot(owner);
        inspectPage(root);

        if (root != null && !root.isLeafPage()) {
            Page<?, ?> minPage = tree.getPage(tree.getKeyPrototype().getMinKey(), owner);
            if (minPage != null) {
                assert minPage.isLeafPage();
                inspectPage(minPage);
                tree.getPageBuffer().unfix(minPage, owner);
            }
        }
        tree.getPageBuffer().unfix(root, owner);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Iterable<K> keys) {
        log.info("Executing show stats for " + database.getIdentifier());

        target.println("Database: " + database.getIdentifier());
        // Disable statistics showing for inspect
        Configuration.instance().setShowStatistics(false);

        Tree<K, V, ?> tree = database.getDatabaseTree();
        describeTree(tree);
        inspectDB();

        if (database instanceof CMVBTDatabase<?, ?>) {
            CMVBTDatabase<K, V> cmDB = (CMVBTDatabase<K, V>) database;

            Transaction<K, V> tx = cmDB.beginTransaction();
            for (int i = 0; i < 500; i++) {
                tx.insert(cmDB.getKeyPrototype().parse(String.valueOf(i)), (V) cmDB
                    .getValuePrototype().parse("0"));
            }
            VersionedBTree<K, V> vbt = cmDB.getVBT();
            describeTree(vbt);
        }

    }

    @Override
    public boolean requiresKeys() {
        return false;
    }

}
