package com.fcareer.common.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class LruCache<K, V> {

    private final Map<K, V> entries;

    public LruCache(int maxEntries) {
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized V get(K key) {
        return entries.get(key);
    }

    public synchronized void put(K key, V value) {
        entries.put(key, value);
    }

    public V computeIfAbsent(K key, Function<K, V> loader) {
        V cached = get(key);
        if (Objects.nonNull(cached)) {
            return cached;
        }
        V loaded = loader.apply(key);
        put(key, loaded);
        return loaded;
    }

    public synchronized void remove(K key) {
        entries.remove(key);
    }

    public synchronized void removeIf(BiPredicate<K, V> predicate) {
        entries.entrySet().removeIf((e) -> predicate.test(e.getKey(), e.getValue()));
    }

    public synchronized int size() {
        return entries.size();
    }
}
