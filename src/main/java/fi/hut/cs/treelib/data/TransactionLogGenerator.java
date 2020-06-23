package fi.hut.cs.treelib.data;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.action.Action;
import fi.hut.cs.treelib.action.ActionReader;
import fi.tuska.util.iterator.ReaderIterator;

public class TransactionLogGenerator<K extends Key<K>, V extends PageValue<?>> {

    public static final String BEAN_NAME = "logGenerator";
    /**
     * Currently this must be changed to switch between generating integer/MBR
     * tx logs
     */
    private static final String CFG_PATH = "log-generator-int.xml";
    private static final Logger log = Logger.getLogger(TransactionLogGenerator.class);

    private final ActionGenerator<K, V> generator;
    private final PrintStream logTarget;
    private final PrintStream msgTarget;
    private final long reportInterval;
    private long generated;
    private long counter;

    public TransactionLogGenerator(ActionGenerator<K, V> generator, long reportInterval) {
        this(generator, reportInterval, System.err, System.out);
    }

    public TransactionLogGenerator(ActionGenerator<K, V> generator, long reportInterval,
        PrintStream logTarget, PrintStream msgTarget) {
        this.generator = generator;
        this.logTarget = logTarget;
        this.msgTarget = msgTarget;
        this.reportInterval = reportInterval;
        this.generated = 0;
        this.counter = 0;
    }

    public void setInitialState(String[] fileNames) {
        try {
            for (String fileName : fileNames) {
                log.info("Setting initial state from file " + fileName);
                generator.applyActions(new ActionReader<K, V>(generator.getKeyPrototype(),
                    generator.getValuePrototype()).iterator(new ReaderIterator(new FileReader(
                    fileName))));
                log.info("Alive entries after " + fileName + ": " + generator.getAliveKeyCount());
                log.info("All inserted entries after " + fileName + ": "
                    + generator.getAllInsertedKeyCount());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        log.info("Initializing application context");
        ApplicationContext ctx = new ClassPathXmlApplicationContext(CFG_PATH);
        TransactionLogGenerator<?, ?> generator = (TransactionLogGenerator<?, ?>) ctx
            .getBean(BEAN_NAME);
        generator.generate();
    }

    public void generate() {
        msgTarget.println("Starting generation");
        for (Action<?, ?> action : generator) {
            logTarget.println(action.writeToLog());

            generated++;
            counter++;
            if (counter >= reportInterval) {
                msgTarget.println(generated);
                counter = 0;
            }
        }
        msgTarget.println("Complete: " + generated);
        log.info("Alive entries at the end of generation: " + generator.getAliveKeyCount());
    }
}
