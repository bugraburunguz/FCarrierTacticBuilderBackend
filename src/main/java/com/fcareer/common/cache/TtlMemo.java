package com.fcareer.common.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** Referans veri için basit süreli bellek önbelleği (rol/davranış tag'i gibi nadir değişen tablolar); her çağrıda DB yükünü önler (BUG-17). */
public final class TtlMemo<T> {

    private final Duration ttl;
    private final Supplier<T> loader;
    private volatile T value;
    private volatile long loadedAt;

    public TtlMemo(Duration ttl, Supplier<T> loader) {
        this.ttl = ttl;
        this.loader = loader;
    }

    public T get() {
        long now = System.nanoTime();
        T current = value;
        if (Objects.isNull(current) || now - loadedAt > ttl.toNanos()) {
            current = loader.get();
            value = current;
            loadedAt = now;
        }
        return current;
    }

    public void invalidate() {
        value = null;
    }
}
