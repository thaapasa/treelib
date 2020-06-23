package fi.hut.cs.treelib.stats;

import java.io.File;
import java.io.PrintStream;

import fi.hut.cs.treelib.Database;

public interface StatisticsPrinter {

    void showStatistics(StatisticsLogger stats, Database<?, ?, ?> db, String title);

    void showStatistics(Statistics stats, Database<?, ?, ?> db, String title);

    void setTargetStream(PrintStream stream);

    void setTargetFile(File file);

    /**
     * Extra information that will be output alongside the statistics. Set to
     * null to disable.
     */
    void setExtraTestInfo(String info);

}
