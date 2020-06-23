package fi.hut.cs.treelib.simulator;

import java.io.File;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;

public class LogSimulator<K extends Key<K>, V extends PageValue<?>> extends Simulator<K, V> {

    private final File logFile;

    public LogSimulator(Database<K, V, ?> database, File logFile) {
        super(database);
        this.logFile = logFile;
    }

    @Override
    public void simulate() {
        Program p = new ActionReaderProgram<K, V>(database, logFile, writer);
        p.run();
    }

    @Override
    public String toString() {
        return "Log file simulator";
    }

}
