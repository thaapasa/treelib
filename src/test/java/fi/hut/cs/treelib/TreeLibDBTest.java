package fi.hut.cs.treelib;

import java.util.ArrayList;
import java.util.List;

import fi.hut.cs.treelib.common.NonThrashingSMOPolicy;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.tuska.util.Pair;

public abstract class TreeLibDBTest<K extends Key<K>, V extends PageValue<?>> extends TreeLibTest {

    protected final K keyProto;
    protected final V valueProto;
    protected final SMOPolicy nonThrashingPolicy = new NonThrashingSMOPolicy(0.2, 0.2);

    protected TreeLibDBTest(K keyProto, V valueProto) {
        this.keyProto = keyProto;
        this.valueProto = valueProto;
    }

    protected KeyRange<K> kr(int min, int max) {
        return new KeyRangeImpl<K>(k(min), k(max));
    }

    protected K k(int key) {
        return keyProto.parse(String.valueOf(key));
    }

    @SuppressWarnings("unchecked")
    protected V v(int value) {
        return (V) valueProto.parse(String.valueOf(value));
    }

    protected List<Pair<K, V>> kvl(int... kvs) {
        assert kvs.length % 2 == 00;
        List<Pair<K, V>> list = new ArrayList<Pair<K, V>>(kvs.length / 2);
        for (int i = 0; i < kvs.length / 2; i++) {
            list.add(new Pair<K, V>(k(kvs[i * 2]), v(kvs[i * 2 + 1])));
        }
        return list;
    }

    protected void check(Object o) {
        if (o instanceof Component)
            ((Component) o).checkConsistency(true);
    }

}
