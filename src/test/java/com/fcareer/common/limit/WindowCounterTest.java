package com.fcareer.common.limit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** OPS-02: Redis yerine bellek içi sabit pencereli sayaç (tek backend örneği). */
class WindowCounterTest {

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
    private final WindowCounter counter = new WindowCounter(clock);

    @Test
    void countsWithinTheWindowAndRestartsAfterIt() {
        assertThat(counter.increment("k", Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(counter.increment("k", Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(counter.current("k")).isEqualTo(2);
        assertThat(counter.current("other")).isZero();

        clock.now = clock.now.plusSeconds(61);

        assertThat(counter.current("k")).as("pencere bitti").isZero();
        assertThat(counter.increment("k", Duration.ofMinutes(1))).isEqualTo(1);
    }

    @Test
    void expiredKeysAreSweptSoMemoryStaysBounded() {
        for (int i = 0; i < 5_000; i++) {
            counter.increment("minute-" + i, Duration.ofMinutes(1));
        }
        clock.now = clock.now.plusSeconds(120);
        for (int i = 0; i < 2_000; i++) {
            counter.increment("fresh-" + i, Duration.ofMinutes(1));
        }

        assertThat(counter.size()).as("süresi dolan anahtarlar süpürülür").isLessThanOrEqualTo(2_000 + WindowCounter.SWEEP_EVERY);
    }
}
