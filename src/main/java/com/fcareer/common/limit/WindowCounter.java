package com.fcareer.common.limit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sabit pencereli sayaç (hız sınırı, kayıt sınırı, admin kilidi). Bellek içi: backend tek örnek çalışır (OPS-02, Redis kaldırıldı).
 * Anahtar ilk artışta pencere süresi kadar yaşar; süresi dolanlar her {@link #SWEEP_EVERY} artışta süpürülür, bellek sınırlı kalır.
 * Yeniden başlatmada sayaçlar sıfırlanır.
 */
@Component
public class WindowCounter {

    static final int SWEEP_EVERY = 1024;

    private record Entry(long count, long expiresAtMillis) {
    }

    private final Clock clock;
    private final Map<String, Entry> counts = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();

    public WindowCounter(Clock clock) {
        this.clock = clock;
    }

    /** Sayacı bir artırıp yeni değeri döner. */
    public long increment(String key, Duration window) {
        long now = clock.millis();
        if (operations.incrementAndGet() % SWEEP_EVERY == 0) {
            counts.values().removeIf(entry -> entry.expiresAtMillis() <= now);
        }
        return counts.compute(key, (k, current) -> Objects.isNull(current) || current.expiresAtMillis() <= now
                ? new Entry(1, now + window.toMillis())
                : new Entry(current.count() + 1, current.expiresAtMillis())).count();
    }

    /** Sayacı artırmadan okur; yoksa ya da süresi dolduysa 0. */
    public long current(String key) {
        Entry entry = counts.get(key);
        return Objects.isNull(entry) || entry.expiresAtMillis() <= clock.millis() ? 0 : entry.count();
    }

    int size() {
        return counts.size();
    }
}
