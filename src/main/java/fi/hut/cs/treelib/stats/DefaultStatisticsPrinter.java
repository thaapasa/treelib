package fi.hut.cs.treelib.stats;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;

/**
 * Class for dumping statistics information to screen (in
 * human/machine-readable form).
 * 
 * @author tuska
 */
public class DefaultStatisticsPrinter implements StatisticsPrinter {

    private static final Logger log = Logger.getLogger(DefaultStatisticsPrinter.class);

    protected static final DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    protected static final String NUM_FORMAT = "%.3f";
    protected static final String TIME_F = "time-measurement:" + NUM_FORMAT;
    protected static final String AVGMINMAX_F = "%s\tavg:" + NUM_FORMAT + "\tmin:" + NUM_FORMAT
        + "\tmax:" + NUM_FORMAT;
    protected static final String AVGMINMAXCOUNT_F = "%s\tavg:" + NUM_FORMAT + "\tmin:"
        + NUM_FORMAT + "\tmax:" + NUM_FORMAT + "\tcount:%d";

    private File targetFile;
    private PrintStream targetStream = System.out;

    private FileOutputStream fos;
    private String extraTestInfo;

    public DefaultStatisticsPrinter() {
    }

    @Override
    public void setTargetFile(File targetFile) {
        this.targetFile = targetFile;
    }

    @Override
    public void setTargetStream(PrintStream targetStream) {
        this.targetStream = targetStream;
    }

    protected PrintStream getTarget() {
        if (targetFile == null) {
            return targetStream;
        }
        try {
            fos = new FileOutputStream(targetFile, true);
            return new PrintStream(fos);
        } catch (FileNotFoundException e) {
            log.error(e, e);
            return null;
        }
    }

    protected void releaseTarget(PrintStream output) {
        System.out.flush();
        System.err.flush();
        output.flush();
        if (fos != null) {
            try {
                fos.flush();
                fos.close();
            } catch (IOException e) {
                log.warn(e, e);
            }
            fos = null;
        }
    }

    public String getExtraTestInfo() {
        return extraTestInfo;
    }

    @Override
    public void setExtraTestInfo(String extraTestInfo) {
        this.extraTestInfo = extraTestInfo;
    }

    /**
     * Prints the given statistics to the given PrintStream.
     */
    @Override
    public void showStatistics(StatisticsLogger stats, Database<?, ?, ?> db, String title) {
        stats.pauseStatistics();
        showStatistics(stats.getStatistics(), db, title);
        stats.continueStatistics();
    }

    /**
     * Prints the given statistics to the given PrintStream.
     */
    @Override
    public void showStatistics(Statistics stats, Database<?, ?, ?> db, String title) {
        System.out.flush();
        System.err.flush();
        log.debug("Show statistics: " + stats);

        PrintStream stt = getTarget();

        stt.println("title: " + title);
        if (stats == null) {
            stt.println("no-statistics-defined");
            return;
        }
        stt.println("start-time:" + format.format(stats.getStartTime()));
        stt.println("end-time:" + format.format(stats.getEndTime()));
        stt.println("database:" + db.getIdentifier());
        stt.println("buffer-size:" + db.getPageBuffer().getBufferSize());
        stt.println("page-size:" + db.getPageBuffer().getPageSize());
        if (extraTestInfo != null)
            stt.println("extra:" + extraTestInfo);
        stt.println(String.format("operation-count:%d", stats.getActionCount()));
        stt.println(String.format(TIME_F, stats.getElapsedTime()));

        int maxNameLen = 25;
        for (Statistics.GlobalOperation operation : Statistics.GlobalOperation.values()) {
            if (stats.hasGlobalOperationStatistics(operation)) {
                stt.println(String.format("%s\t%d", padToLength(operation.toString()
                    .toLowerCase(), maxNameLen), stats.getGlobalOperationCount(operation)));
            }
        }
        for (Statistics.Action action : Statistics.Action.values()) {
            long amount = stats.getActionCount(action);
            if (amount != 0) {
                stt.println(String.format("%s\tcount:%d", padToLength(action.toString()
                    .toLowerCase(), maxNameLen), stats.getActionCount(action)));
            }
        }
        for (Statistics.Operation operation : Statistics.Operation.values()) {
            if (stats.hasOperationStatistics(operation)) {
                stt.println(String.format(AVGMINMAX_F, padToLength(operation.toString()
                    .toLowerCase(), maxNameLen), stats.getOperationAverage(operation), stats
                    .getOperationMin(operation), stats.getOperationMax(operation)));
            }
        }
        for (Statistics.Value value : Statistics.Value.values()) {
            if (stats.hasValueStatistics(value)) {
                stt.println(String.format(AVGMINMAXCOUNT_F, padToLength(value.toString()
                    .toLowerCase(), maxNameLen), stats.getValueAverage(value), stats
                    .getValueMin(value), stats.getValueMax(value), stats.getValueCount(value)));
            }
        }
        stt.println();

        releaseTarget(stt);
    }

    protected static String padToLength(String str, int len) {
        if (str.length() >= len)
            return str;
        StringBuilder b = new StringBuilder(str);
        int diff = len - str.length();
        while (diff-- > 0)
            b.append(" ");
        return b.toString();
    }

    @Override
    public String toString() {
        return "Default statistics printer";
    }

}
