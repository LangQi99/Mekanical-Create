package io.github.langqi99.mekanicalcreate.content;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small access-ordered cache with a strict entry limit. */
final class BoundedLruCache<K, V> {
    private final int maximumSize;
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75F, true);

    BoundedLruCache(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.maximumSize = maximumSize;
    }

    V get(K key) {
        return entries.get(key);
    }

    boolean containsKey(K key) {
        return entries.containsKey(key);
    }

    void put(K key, V value) {
        entries.put(key, value);
        while (entries.size() > maximumSize) {
            K eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
        }
    }

    void remove(K key) {
        entries.remove(key);
    }

    int size() {
        return entries.size();
    }

    void clear() {
        entries.clear();
    }

    Map<K, V> snapshot() {
        return Map.copyOf(entries);
    }
}
