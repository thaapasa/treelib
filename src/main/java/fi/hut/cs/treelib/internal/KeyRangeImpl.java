package fi.hut.cs.treelib.internal;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.Storable;

public class KeyRangeImpl<K extends Key<K>> extends KeyRange<K> implements
    Comparable<KeyRange<K>>, Storable<KeyRange<K>> {

    private static final Logger log = Logger.getLogger(KeyRangeImpl.class);

    public KeyRangeImpl(K onlyKey) {
        super(onlyKey);
    }

    public KeyRangeImpl(K min, K max) {
        super(min, max);
    }

    public KeyRangeImpl(KeyRange<K> other) {
        super(other);
    }

    /**
     * @return the smallest keyrange that includes this key. The returned
     * range contains only the given key.
     */
    public static <E extends Key<E>> KeyRange<E> getKeyRange(E key) {
        if (key == null) {
            log.debug("Returning null key range for null key");
            return null;
        }
        return new KeyRangeImpl<E>(key);
    }

    @Override
    protected KeyRange<K> instantiate(K min, K max) {
        return new KeyRangeImpl<K>(min, max);
    }

    @Override
    protected KeyRange<K> instantiate(K onlyKey) {
        return new KeyRangeImpl<K>(onlyKey);
    }

}
