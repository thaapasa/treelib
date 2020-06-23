package fi.hut.cs.treelib.action;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.internal.KeyRangeImpl;

/**
 * A reader class for reading Actions stored in a transaction log. Provides a
 * convenience iterator for parsing Actions from a String source.
 * 
 * @author thaapasa
 */
public class ActionReader<K extends Key<K>, V extends PageValue<?>> {

    private final K keyProto;
    private final V valueProto;
    private final Map<String, Action<K, V>> actionProtos;
    private static final Logger log = Logger.getLogger(ActionReader.class);

    public ActionReader(K keyProto, V valueProto) {
        this.keyProto = keyProto;
        this.valueProto = valueProto;
        this.actionProtos = new HashMap<String, Action<K, V>>();
        addActionProto(new BeginTransactionAction<K, V>());
        addActionProto(new BeginReadTransactionAction<K, V>());
        addActionProto(new CommitTransactionAction<K, V>());
        addActionProto(new InsertAction<K, V>(this.keyProto, this.valueProto));
        addActionProto(new QueryAction<K, V>(this.keyProto));
        addActionProto(new DeleteAction<K, V>(this.keyProto));
        addActionProto(new RangeQueryAction<K, V>(new KeyRangeImpl<K>(this.keyProto)));
        addActionProto(new StatisticsShowAction<K, V>("proto"));
    }

    private void addActionProto(Action<K, V> action) {
        actionProtos.put(action.getLogIdentifier().toLowerCase(), action);
    }

    public Action<K, V> read(String logLine) {
        if (logLine == null)
            return null;
        logLine = logLine.trim();
        if (logLine.equals(""))
            return null;
        int pos = logLine.indexOf(Action.LOG_SPLITTER);
        String identifier = pos > 0 ? logLine.substring(0, pos) : logLine;
        identifier = identifier.toLowerCase().trim();
        Action<K, V> proto = actionProtos.get(identifier);
        if (proto == null) {
            if (identifier.length() > 1) {
                proto = actionProtos.get(identifier.substring(0, 1));
            }
            if (proto == null) {
                log.warn("Could not read action with identifier <" + identifier + ">, from "
                    + logLine);
                return null;
            }
        }
        return proto.readFromLog(logLine);
    }

    /**
     * @return an iterator that reads all the strings from the source iterator
     * as actions
     */
    public Iterator<Action<K, V>> iterator(final Iterator<String> source) {
        return new Iterator<Action<K, V>>() {

            private Action<K, V> nextAction = null;

            private void findNext() {
                if (nextAction != null)
                    return;
                while (nextAction == null) {
                    if (!source.hasNext()) {
                        // No more actions
                        return;
                    }
                    String line = source.next();
                    assert line != null;
                    nextAction = read(line);
                }
            }

            @Override
            public boolean hasNext() {
                findNext();
                return nextAction != null;
            }

            @Override
            public Action<K, V> next() {
                findNext();
                if (nextAction == null)
                    throw new NoSuchElementException();

                Action<K, V> res = nextAction;
                // Clear next item
                nextAction = null;

                assert res != null;
                return res;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove() not supported");
            }
        };
    }
}
