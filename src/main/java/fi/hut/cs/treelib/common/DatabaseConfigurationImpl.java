package fi.hut.cs.treelib.common;

import fi.hut.cs.treelib.DatabaseConfiguration;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.concurrency.LatchManager;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageStorage;

public class DatabaseConfigurationImpl<K extends Key<K>, V extends PageValue<?>> implements
    DatabaseConfiguration<K, V> {

    protected final K keyPrototype;
    protected final V valuePrototype;
    protected final int pageSize;
    protected final int bufferSize;
    protected final SMOPolicy smoPolicy;

    protected final LatchManager latchManager;
    protected final PageBuffer pageBuffer;
    protected final PageStorage pageStorage;

    protected DatabaseConfigurationImpl(K keyPrototype, V valuePrototype, int pageSize,
        int bufferSize, SMOPolicy smoPolicy, PageStorage pageStorage, LatchManager latchManager) {
        this.keyPrototype = keyPrototype;
        this.valuePrototype = valuePrototype;
        this.pageSize = pageSize;
        this.bufferSize = bufferSize;
        this.smoPolicy = smoPolicy;
        this.pageStorage = pageStorage;
        this.latchManager = latchManager;
        this.pageBuffer = new PageBuffer(pageStorage, bufferSize, latchManager);
        this.pageBuffer.initialize();
    }

    public DatabaseConfigurationImpl(DatabaseConfiguration<K, ?> cfg, V valuePrototype,
        SMOPolicy smoPolicy) {
        this(cfg, cfg.getKeyPrototype(), valuePrototype, smoPolicy);
    }

    public DatabaseConfigurationImpl(DatabaseConfiguration<?, ?> cfg, K keyPrototype,
        V valuePrototype, SMOPolicy smoPolicy) {
        this.keyPrototype = keyPrototype;
        this.valuePrototype = valuePrototype;
        this.pageSize = cfg.getPageSize();
        this.bufferSize = cfg.getBufferSize();
        this.smoPolicy = smoPolicy;
        this.pageStorage = cfg.getPageStorage();
        this.pageBuffer = cfg.getPageBuffer();
        this.latchManager = cfg.getLatchManager();
    }

    @Override
    public K getKeyPrototype() {
        return keyPrototype;
    }

    @Override
    public V getValuePrototype() {
        return valuePrototype;
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public PageBuffer getPageBuffer() {
        return pageBuffer;
    }

    @Override
    public PageStorage getPageStorage() {
        return pageStorage;
    }

    @Override
    public LatchManager getLatchManager() {
        return latchManager;
    }

    @Override
    public SMOPolicy getSMOPolicy() {
        return smoPolicy;
    }

}
