package fi.hut.cs.treelib.action;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.stats.DefaultStatisticsPrinter;
import fi.hut.cs.treelib.stats.StatisticsLogger;
import fi.hut.cs.treelib.stats.StatisticsPrinter;

/**
 * Action that shows the statistics and resets them.
 * 
 * @author thaapasa
 */
public class StatisticsShowAction<K extends Key<K>, V extends PageValue<?>> implements
    Action<K, V> {

    private static final String LOG_ID = "s";
    private final String title;
    private StatisticsPrinter statisticsPrinter = new DefaultStatisticsPrinter();

    public StatisticsShowAction(String title) {
        this.title = title;
    }

    @Override
    public Transaction<K, V> perform(Database<K, V, ?> database, Transaction<K, V> transaction) {
        StatisticsLogger stats = database.getStatisticsLogger();
        if (!stats.isStarted())
            throw new IllegalStateException("Statistics not started");
        statisticsPrinter.showStatistics(stats, database, title);
        stats.clear();
        stats.startStatistics();
        return transaction;
    }

    @Override
    public String toString() {
        return "Action: show statistics";
    }

    @Override
    public StatisticsShowAction<K, V> readFromLog(String str) {
        String parts[] = str.split(LOG_SPLITTER);
        if (parts.length < 2) {
            return new StatisticsShowAction<K, V>("action");
        } else {
            return new StatisticsShowAction<K, V>(parts[1]);
        }
    }

    @Override
    public String writeToLog() {
        return writeActionToLog(title);
    }

    public static String writeActionToLog(String title) {
        return LOG_ID + LOG_SPLITTER + title;
    }

    @Override
    public String getLogIdentifier() {
        return LOG_ID;
    }

    @Override
    public K getKey() {
        return null;
    }

}
