package fi.hut.cs.treelib.stats;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;

public class SQLStatisticsPrinter extends DefaultStatisticsPrinter {

    private static final Logger log = Logger.getLogger(SQLStatisticsPrinter.class);

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final NumberFormat DECIMAL_FORMAT = new DecimalFormat("#.########",
        DecimalFormatSymbols.getInstance(Locale.ENGLISH));

    public static String slashify(String src) {
        return src.replace("'", "''");
    }

    public static String getDateTime(Date date) {
        return DATE_FORMAT.format(date);
    }

    public SQLStatisticsPrinter() {
        setExtraTestInfo(null);
    }

    @Override
    public void setExtraTestInfo(String info) {
        // Disallow nulls for this class
        super.setExtraTestInfo(info != null ? info : "");
    }

    /**
     * Prints the given statistics to the given PrintStream.
     */
    @Override
    public void showStatistics(Statistics stats, Database<?, ?, ?> db, String test) {
        System.out.flush();
        System.err.flush();
        log.debug("Show statistics " + test + ": " + stats);

        if (stats == null) {
            log.warn("No statistics defined");
            return;
        }
        PrintStream stt = getTarget();

        stt.println("-- Statistics for " + db.getIdentifier() + " test: " + test);
        stt.println(String.format("INSERT INTO test_info (test, start_time, end_time, "
            + "db, buffer_size, page_size, ops, elapsed, extra) VALUES"
            + "('%s', '%s', '%s', '%s', %d, %d, %d, %s, '%s');", slashify(test),
            getDateTime(stats.getStartTime()), getDateTime(stats.getEndTime()), slashify(db
                .getIdentifier()), db.getPageBuffer().getBufferSize(), db.getPageBuffer()
                .getPageSize(), stats.getActionCount(), DECIMAL_FORMAT.format(stats
                .getElapsedTime()), slashify(getExtraTestInfo())));

        for (Statistics.GlobalOperation operation : Statistics.GlobalOperation.values()) {
            if (stats.hasGlobalOperationStatistics(operation)) {
                stt.println(String.format("INSERT INTO test_ops (test, db, start_time, "
                    + "op, amount, extra) VALUES ('%s', '%s', '%s', '%s', %d, '%s');",
                    slashify(test), slashify(db.getIdentifier()), getDateTime(stats
                        .getStartTime()), operation.toString().toLowerCase(), stats
                        .getGlobalOperationCount(operation), slashify(getExtraTestInfo())));
            }
        }
        for (Statistics.Action action : Statistics.Action.values()) {
            long amount = stats.getActionCount(action);
            if (amount != 0) {
                stt.println(String.format("INSERT INTO test_ops (test, db, start_time, "
                    + "op, amount, extra) VALUES ('%s', '%s', '%s', '%s', %d, '%s');",
                    slashify(test), slashify(db.getIdentifier()), getDateTime(stats
                        .getStartTime()), action.toString().toLowerCase(), stats
                        .getActionCount(action), slashify(getExtraTestInfo())));
            }
        }
        for (Statistics.Operation operation : Statistics.Operation.values()) {
            if (stats.hasOperationStatistics(operation)) {
                stt.println(String.format("INSERT INTO test_vals (test, db, start_time, "
                    + "op, avgval, deviation, minval, maxval, extra) VALUES "
                    + "('%s', '%s', '%s', '%s', %s, %s, %s, %s, '%s');", slashify(test),
                    slashify(db.getIdentifier()), getDateTime(stats.getStartTime()), operation
                        .toString().toLowerCase(), DECIMAL_FORMAT.format(stats
                        .getOperationAverage(operation)), DECIMAL_FORMAT.format(stats
                        .getOperationDeviation(operation)), DECIMAL_FORMAT.format(stats
                        .getOperationMin(operation)), DECIMAL_FORMAT.format(stats
                        .getOperationMax(operation)), slashify(getExtraTestInfo())));
            }
        }
        for (Statistics.Value value : Statistics.Value.values()) {
            if (stats.hasValueStatistics(value)) {
                stt.println(String.format("INSERT INTO test_vals (test, db, start_time, "
                    + "op, avgval, minval, maxval), extra VALUES "
                    + "('%s', '%s', '%s', '%s', %s, %s, %s, '%s');", slashify(test), slashify(db
                    .getIdentifier()), getDateTime(stats.getStartTime()), value.toString()
                    .toLowerCase(), DECIMAL_FORMAT.format(stats.getValueAverage(value)),
                    DECIMAL_FORMAT.format(stats.getValueMin(value)), DECIMAL_FORMAT.format(stats
                        .getValueMax(value)), slashify(getExtraTestInfo())));
            }
        }
        stt.println();

        releaseTarget(stt);
    }

    @Override
    public String toString() {
        return "SQL statistics printer";
    }

}
