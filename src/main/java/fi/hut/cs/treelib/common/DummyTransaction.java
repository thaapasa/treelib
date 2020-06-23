package fi.hut.cs.treelib.common;

import java.util.List;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.tuska.util.Callback;
import fi.tuska.util.Pair;

public class DummyTransaction<K extends Key<K>, V extends PageValue<?>> implements
    Transaction<K, V> {

    private int readVersion;
    private int transactionID;
    private boolean isReadOnly = false;
    private final int ownerID;
    private final String name;

    public DummyTransaction(String name) {
        this.name = name;
        this.ownerID = OwnerImpl.createID();
        this.readVersion = AbstractTree.DEFAULT_READ_VERSION;
        this.transactionID = AbstractTree.DEFAULT_TRANSACTION_ID;
    }

    public DummyTransaction(Owner owner) {
        this.name = owner.getName();
        this.ownerID = owner.getOwnerID();
        this.readVersion = AbstractTree.DEFAULT_READ_VERSION;
        this.transactionID = AbstractTree.DEFAULT_TRANSACTION_ID;
    }

    public DummyTransaction(int readVersion, int transactionID, Owner owner) {
        this.name = owner.getName();
        this.ownerID = owner.getOwnerID();
        this.readVersion = readVersion;
        this.transactionID = transactionID;
    }

    public void set(int readVersion, int txID, boolean readOnly) {
        this.readVersion = readVersion;
        this.transactionID = txID;
        this.isReadOnly = readOnly;
    }

    public void setReadVersion(int ver) {
        this.readVersion = ver;
    }

    public void setTransactionID(int id) {
        this.transactionID = id;
    }

    public void setReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
    }

    @Override
    public int getReadVersion() {
        return readVersion;
    }

    @Override
    public int getTransactionID() {
        return transactionID;
    }

    @Override
    public void abort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(K key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean delete(K key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V get(K key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getCommitVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Pair<K, V>> getRange(KeyRange<K> range) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getRange(KeyRange<K> range, Callback<Pair<K, V>> callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getAll(final Callback<Pair<K, V>> callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Pair<K, V>> getAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean insert(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReadOnly() {
        return isReadOnly;
    }

    @Override
    public boolean isUpdating() {
        return !isReadOnly;
    }

    @Override
    public int getOwnerID() {
        return ownerID;
    }

    @Override
    public String toString() {
        return String.format("Dummy TX %s (tx id: %d, read ver %d, owner: %d)", name,
            transactionID, readVersion, ownerID);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDebugID() {
        return String.format("dTX:%d:R:%d", transactionID, readVersion);
    }

}
