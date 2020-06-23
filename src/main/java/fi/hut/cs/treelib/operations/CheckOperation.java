package fi.hut.cs.treelib.operations;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.MDDatabase;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.internal.MDRangeCheckOperation;
import fi.hut.cs.treelib.internal.PageCheckOperation;
import fi.hut.cs.treelib.mdtree.JTreeDatabase;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.Statistics.Action;
import fi.hut.cs.treelib.util.KeyRangePredicate;
import fi.hut.cs.treelib.util.MBRPredicate;

public class CheckOperation<K extends Key<K>, V extends PageValue<?>> implements Operation<K> {

    private static final Logger log = Logger.getLogger(CheckOperation.class);

    private final Owner owner = new OwnerImpl("check");

    private Database<K, V, ?> database;
    private StatisticsLogger statisticsLogger;

    public CheckOperation(Database<K, V, ?> database, StatisticsLogger statisticsLogger) {
        this.database = database;
        this.statisticsLogger = statisticsLogger;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Iterable<K> keys) {
        statisticsLogger.newAction(Action.ACTION_SPECIAL);

        log.info("Running consistency check for " + database);
        database.checkConsistency();
        log.info("Consistency check completed");

        if (database instanceof MDDatabase) {
            log.info("Running page check for a multidimensional DB");
            MDDatabase<K, IntegerValue, ?> mdDB = (MDDatabase<K, IntegerValue, ?>) database;
            PageCheckOperation<MBR<K>, IntegerValue> op = (database instanceof JTreeDatabase) ? new MDRangeCheckOperation<K, IntegerValue>(
                database.getDatabaseTree().getHeight(), statisticsLogger)
                : new PageCheckOperation<MBR<K>, IntegerValue>(statisticsLogger);
            mdDB.traverseMDPages(new MBRPredicate<K>(), op, owner);
            System.out.println(op.getSummary());
        } else {
            log.info("Running page check");
            PageCheckOperation<K, V> op = new PageCheckOperation<K, V>(statisticsLogger);
            database.traversePages(new KeyRangePredicate<K>(), op, owner);
            System.out.println(op.getSummary());
        }

    }

    @Override
    public boolean requiresKeys() {
        return false;
    }

}
