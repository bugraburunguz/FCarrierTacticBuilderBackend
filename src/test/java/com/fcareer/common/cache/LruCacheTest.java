package com.fcareer.common.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LruCacheTest {

    @Test
    void evictsTheLeastRecentlyUsedEntryBeyondTheLimit() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a");

        cache.put("c", 3);

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("a")).isEqualTo(1);
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("c")).isEqualTo(3);
    }

    @Test
    void computesOnceAndRemovesByPredicate() {
        LruCache<String, Integer> cache = new LruCache<>(10);
        AtomicInteger loads = new AtomicInteger();

        cache.computeIfAbsent("x", (k) -> loads.incrementAndGet());
        cache.computeIfAbsent("x", (k) -> loads.incrementAndGet());
        cache.put("y", 5);
        cache.removeIf((k, v) -> k.equals("y"));

        assertThat(loads).hasValue(1);
        assertThat(cache.get("y")).isNull();
        assertThat(cache.size()).isEqualTo(1);
    }
}
