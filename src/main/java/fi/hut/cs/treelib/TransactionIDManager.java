package fi.hut.cs.treelib;

public interface TransactionIDManager<K extends Key<K>> {

    /**
     * Query the manager to retrieve the commit-time version number of the
     * transaction with the given tx ID.
     * 
     * @param transactionID the temporary transaction ID
     * @return the commit-time version corresponding to the given transaction
     * ID, if the transaction has committed; null if the transaction has not
     * committed
     */
    Integer getCommitVersion(int transactionID);

    /**
     * Called to notify the manager that an entry with a temporary ID has been
     * inserted to a page. Used in TSB to maintain the reference counts of the
     * temporary transaction IDs.
     */
    void notifyTempInserted(K key, int transactionID);

    /**
     * Called to notify the manager that an entry with a temporary ID has been
     * deleted from a page (converted to a proper ID). Used in TSB to maintain
     * the reference counts of the temporary transaction IDs.
     */
    void notifyTempConverted(K key, int transactionID, int commitTime);

}
