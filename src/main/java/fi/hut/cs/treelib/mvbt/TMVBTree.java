package fi.hut.cs.treelib.mvbt;

import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.hut.cs.treelib.Tree;
import fi.hut.cs.treelib.btree.BTree;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.SingleVersionTree;

public class TMVBTree<K extends Key<K>, V extends PageValue<?>> extends MVBTree<K, V> {

    /*
     * TODO: Initialization of this is done via readInfoPage() that is called
     * from super-constructor. This is bad, as the behaviour of calling
     * overloaded methods from constructors causes non-evident behaviour (such
     * as own instance variables not yet initialized). E.g., if we put an
     * initialization here, it will happen after the call to readInfoPage()
     * from super constructor...
     */
    private int committedVersion;

    protected TMVBTree(PageID infoPageID, BTree<IntegerKey, PageID> rootTree,
        DatabaseConfiguration<K, V> dbConfig) {
        super("tmvbt", "TMVBT", infoPageID, rootTree, dbConfig);
    }

    @Override
    protected void readInfoPage(MVBTInfoPage<K> infoPage) {
        super.readInfoPage(infoPage);
        this.committedVersion = infoPage.getCommittedVersion();
        // TODO: Should we automatically terminate the ongoing transaction?
        // (as it is done now)
        if (getActiveVersion() > committedVersion) {
            this.committedVersion = getActiveVersion();
        }
        assert getActiveVersion() == committedVersion : getActiveVersion() + ", "
            + committedVersion;
    }

    protected void forwardCommittedVersion(int newCommittedVer) {
        if (newCommittedVer < committedVersion) {
            throw new IllegalArgumentException("Trying to re-wind committed version: "
                + newCommittedVer + " < " + committedVersion);
        }
        if (newCommittedVer == committedVersion)
            return;
        if (committedVersion != getActiveVersion()) {
            throw new IllegalStateException(
                "Cannot forward version number while there is an active transaction");
        }
        committedVersion = newCommittedVer;
        activeVersion = newCommittedVer;
    }

    @Override
    protected void writeInfoPage(MVBTInfoPage<K> infoPage) {
        super.writeInfoPage(infoPage);
        infoPage.setCommittedVersion(committedVersion);
    }

    /**
     * Override new action handling so that version number is not advanced.
     * 
     * @return the version number (not changed in MVBT)
     */
    @Override
    protected int newAction(int requestedVersion, Owner owner) {
        assert requestedVersion == getActiveVersion();
        return getActiveVersion();
    }

    @Override
    public int getCommittedVersion() {
        return committedVersion;
    }

    protected int beginTransaction(Owner owner) {
        assert getActiveVersion() == committedVersion;
        return newVersion(getActiveVersion(), owner);
    }

    protected int commitTransaction(Transaction<K, V> tx) {
        assert getActiveVersion() == committedVersion + 1;
        committedVersion++;
        updateInfoPage(tx);
        return committedVersion;
    }

    @Override
    public Tree<K, V, MVBTPage<K, V>> getVersionTree(int version) {
        if (version > getActiveVersion() || version < 0) {
            return null;
        }
        return new SingleVersionTree<K, V, MVBTPage<K, V>>(this, version);
    }

}
