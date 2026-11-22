package com.fcareer.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Referans verisi (roller, davranış etiketleri, hazır taktikler) için bellek içi TTL önbelleği (OPS-02, Redis kaldırıldı). Değer JSON olarak
 * saklanır ve her okumada yeni kopya döner: çağıran listeyi değiştirse de önbellek bozulmaz (Redis'teki davranışla aynı).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceCache {

    private record Entry(String json, long expiresAtMillis) {
    }

    private final ObjectMapper objectMapper;
    private final Clock clock;
    public static final Duration SWEEP_INTERVAL = Duration.ofMinutes(10);

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis = new AtomicLong();

    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        T value = loader.get();
        put(key, value, ttl);
        return value;
    }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        Entry entry = entries.get(key);
        if (Objects.isNull(entry) || entry.expiresAtMillis() <= clock.millis()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(entry.json(), type));
        } catch (JsonProcessingException e) {
            log.warn("Reference cache read skipped key : {} reason : {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        sweepExpired();
        try {
            entries.put(key, new Entry(objectMapper.writeValueAsString(value), clock.millis() + ttl.toMillis()));
        } catch (JsonProcessingException e) {
            log.warn("Reference cache write skipped key : {} reason : {}", key, e.getMessage());
        }
    }

    public int size() {
        return entries.size();
    }

    private void sweepExpired() {
        long now = clock.millis();
        long last = lastSweepMillis.get();
        if (now - last < SWEEP_INTERVAL.toMillis() || !lastSweepMillis.compareAndSet(last, now)) {
            return;
        }
        entries.values().removeIf((entry) -> entry.expiresAtMillis() <= now);
    }

    public void evictPrefixAfterCommit(String prefix) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictPrefix(prefix);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictPrefix(prefix);
            }
        });
    }

    public void evictPrefix(String prefix) {
        entries.keySet().removeIf((key) -> key.startsWith(prefix));
    }

    public void evict(String... keys) {
        for (String key : keys) {
            entries.remove(key);
        }
    }
}
