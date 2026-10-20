package com.fcareer.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** OPS-02: Redis yerine bellek içi referans önbelleği; değerler kopya döner (çağıran önbelleği bozamaz), süre dolunca yeniden yüklenir. */
class ReferenceCacheTest {

    private static final TypeReference<List<String>> LIST = new TypeReference<>() {
    };

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final ReferenceCache cache = new ReferenceCache(new ObjectMapper(), clock);

    @Test
    void loadsOnceWithinTheTtlAndReturnsCopies() {
        AtomicInteger loads = new AtomicInteger();

        List<String> first = cache.getOrLoad("roles", LIST, Duration.ofHours(6), () -> {
            loads.incrementAndGet();
            return new ArrayList<>(List.of("a", "b"));
        });
        first.add("mutated");
        List<String> second = cache.getOrLoad("roles", LIST, Duration.ofHours(6), () -> {
            loads.incrementAndGet();
            return List.of("x");
        });

        assertThat(loads).hasValue(1);
        assertThat(second).containsExactly("a", "b");
    }

    @Test
    void expiresAndEvicts() {
        cache.put("tags", List.of("t1"), Duration.ofMinutes(1));
        assertThat(cache.get("tags", LIST)).contains(List.of("t1"));

        clock.now = clock.now.plusSeconds(61);
        assertThat(cache.get("tags", LIST)).isEmpty();

        cache.put("tags", List.of("t2"), Duration.ofMinutes(1));
        cache.evict("tags", "missing");
        assertThat(cache.get("tags", LIST)).isEmpty();
    }
}
