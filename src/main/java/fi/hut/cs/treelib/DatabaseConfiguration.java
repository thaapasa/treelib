package fi.hut.cs.treelib;

import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.concurrency.LatchManager;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;

/**
 * Interface for transferring the common database components and settings
 * around.
 * 
 * @author thaapasa
 */
public interface DatabaseConfiguration<K extends Key<K>, V extends PageValue<?>> {

    K getKeyPrototype();

    V getValuePrototype();

    int getPageSize();

    int getBufferSize();

    PageBuffer getPageBuffer();

    PageStorage getPageStorage();

    LatchManager getLatchManager();

    SMOPolicy getSMOPolicy();

}
