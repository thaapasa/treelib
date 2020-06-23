package fi.hut.cs.treelib.action;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;

/**
 * Classes implementing this interface model a single instruction in a
 * single-user transaction execution scenario. The action can therefore, for
 * example, do an insertion, or commit the active transaction.
 * 
 * @author thaapasa
 */
public interface Action<K extends Key<K>, V extends PageValue<?>> {

    static final String LOG_SPLITTER = ";";

    /**
     * An action that can be performed on the database. The idea is that the
     * executor updates the reference to the transaction object to the one
     * returned from this method, and performs the next action with the
     * updated transaction reference, and so on.
     * 
     * <p>
     * See an example use case in the ActionExecutor class (in the
     * data-package).
     * 
     * @param database the database to operate on
     * @param transaction the current transaction the executor is in
     * @return the new transaction, if this action changes the transaction
     * (null, if this action commits/aborts the transaction)
     */

    Transaction<K, V> perform(Database<K, V, ?> database, Transaction<K, V> transaction);

    /**
     * Writes this single action to a transaction execution log.
     * 
     * @return the string that can be printed to the log
     */
    String writeToLog();

    /**
     * Reads an action from a transaction log. Note that the stored action
     * must be of correct type! Use the ActionReader class to read any kinds
     * of actions from an input source (transaction log).
     * 
     * <p>
     * The convention is that a.readFromLog(a.writeToLog()).equals(a);
     * 
     * @param str the string
     * @return the action read from the log
     */
    Action<K, V> readFromLog(String str);

    /**
     * @return the key that this action operates on, if such a key is defined.
     */
    K getKey();

    /**
     * @return the string that uniquely identifies log entries of this type in
     * the transaction log. The entries must all start with this string.
     */
    String getLogIdentifier();

}
