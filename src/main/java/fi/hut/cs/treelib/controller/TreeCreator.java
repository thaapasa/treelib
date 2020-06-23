package fi.hut.cs.treelib.controller;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.tuska.util.file.FileUtils;

public class TreeCreator<K extends Key<K>, V extends PageValue<?>> extends
    VisualizerExecutor<K, V> implements VisualizerController<K> {

    private static final Logger log = Logger.getLogger(TreeCreator.class);

    private List<String> operations;
    private Iterator<String> operationIterator;
    private int initialOperationCount = 0;
    private int curOperation = 0;

    public TreeCreator(Database<K, V, ?> database, List<String> operations, K keyPrototype) {
        super(database);
        setOperations(operations);
    }

    public TreeCreator(Database<K, V, ?> database, File file, K keyPrototype) {
        super(database);
        setOperations(FileUtils.readFromFile(file));
    }

    public TreeCreator(Database<K, V, ?> database, String filename, K keyPrototype) {
        this(database, new File(filename), keyPrototype);
    }

    private void setOperations(List<String> operations) {
        this.operations = operations;
        this.operationIterator = operations.iterator();
    }

    public void initComponent() {
        log.info(String.format("Initializing Tree Creator, executing %d/%d operations",
            initialOperationCount, operations.size()));
        for (int i = 0; i < initialOperationCount; i++) {
            step();
        }
    }

    public void setInitialOperationCount(int operationCount) {
        this.initialOperationCount = operationCount;
    }

    @Override
    public boolean signalAdvance() {
        return step();
    }

    @Override
    public boolean runAll() {
        boolean modified = false;
        while (operationIterator.hasNext()) {
            step();
            modified = true;
        }
        return modified;
    }

    protected boolean step() {
        if (!operationIterator.hasNext()) {
            log.debug("Can't step forward, no more operations");
            return false;
        }
        String operation = operationIterator.next();
        curOperation++;
        log.info(String.format("Proceeding to next operation %d: %s", curOperation, operation));

        super.execute(operation);
        return true;
    }

}
