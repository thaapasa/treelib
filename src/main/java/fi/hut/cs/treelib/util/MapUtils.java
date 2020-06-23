package fi.hut.cs.treelib.util;

import java.util.Map;

public class MapUtils {

    private MapUtils() {
        // Private constructor to prevent instantiation
    }

    public static <K> void increaseLongValue(Map<K, Long> map, K key) {
        increaseLongValue(map, key, 1);
    }

    public static <K> void increaseLongValue(Map<K, Long> map, K key, long amount) {
        Long c = map.get(key);
        map.put(key, c != null ? c + amount : new Long(amount));
    }

}
