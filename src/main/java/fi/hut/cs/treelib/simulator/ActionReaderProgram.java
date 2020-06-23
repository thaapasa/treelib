package fi.hut.cs.treelib.simulator;

import java.io.File;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.action.Action;
import fi.hut.cs.treelib.action.ActionReader;
import fi.hut.cs.treelib.action.ActionWriter;
import fi.hut.cs.treelib.operations.ProgressCounter;
import fi.tuska.util.iterator.Iterables;

/**
 * A simple program that executes a given list of actions. Used to read in
 * previously generated action log files.
 * 
 * @author thaapasa
 */
public class ActionReaderProgram<K extends Key<K>, V extends PageValue<?>> implements Program {

    private static final Logger log = Logger.getLogger(ActionReaderProgram.class);

    private final Database<K, V, ?> database;
    private final Iterable<String> actionSrc;
    private final ActionWriter<K, V> writer;

    public ActionReaderProgram(Database<K, V, ?> database, File logFile, ActionWriter<K, V> writer) {
        this.database = database;
        this.actionSrc = Iterables.get(logFile);
        this.writer = writer;
    }

    @Override
    public void run() {
        // Begin TX
        Transaction<K, V> tx = null;
        log.info("Starting to read keys from log file");

        ActionReader<K, V> reader = new ActionReader<K, V>(database.getKeyPrototype(), database
            .getValuePrototype());

        ProgressCounter progress = new ProgressCounter("action");
        long actions = 0;
        for (String logLine : actionSrc) {
            progress.advance();

            Action<K, V> action = reader.read(logLine);
            tx = action.perform(database, tx);
            writer.writeAction(action);

            actions++;
        }

        log.info(actions + " actions read, DB version is now " + database.getCommittedVersion());
    }

}
