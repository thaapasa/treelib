package fi.hut.cs.treelib.operations;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.MDTree;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.internal.InspectPageOperation;
import fi.hut.cs.treelib.util.MBRPredicate;

public class MDInspectOperation<K extends Key<K>, V extends PageValue<?>> extends
    InspectOperation<MBR<K>, V> {

    private MDDatabase<K, V, ?> mdDatabase;

    private final Owner owner = new OwnerImpl("md-inspect");

    public MDInspectOperation(MDDatabase<K, V, ?> database) {
        super(database);
        this.mdDatabase = database;
    }

    @Override
    protected void inspectDB() {
        MDTree<K, V, ?> tree = mdDatabase.getDatabaseTree();

        System.out.println(mdDatabase);
        System.out.println("Last page ID: "
            + tree.getPageBuffer().getPageStorage().getMaxPageID());
        System.out.println("Total page count: " + tree.getPageBuffer().getTotalPageCount());

        InspectPageOperation<MBR<K>, V> op = new InspectPageOperation<MBR<K>, V>(mdDatabase);

        mdDatabase.traverseMDPages(new MBRPredicate<K>(), op, owner);
        System.out.println(op.getSummary());
    }

}
