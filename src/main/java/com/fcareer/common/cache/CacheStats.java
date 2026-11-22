package com.fcareer.common.cache;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public final class CacheStats {

    private final String name;
    private final int logEvery;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong lookups = new AtomicLong();

    public CacheStats(String name, int logEvery) {
        this.name = name;
        this.logEvery = logEvery;
    }

    public boolean record(boolean hit) {
        (hit ? hits : misses).incrementAndGet();
        if (lookups.incrementAndGet() % logEvery != 0) {
            return false;
        }
        log.info("Cache stats cache : {} hit : {} miss : {}", name, hits.get(), misses.get());
        return true;
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }
}
