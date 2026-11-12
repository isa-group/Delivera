package com.delivera.fms.routing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.delivera.fms.routing.config.HealthCacheProperties;
import com.delivera.fms.routing.dto.SolverStatus;

class HealthStatusCacheTest {

    private static final String GREEDY = "http://greedy-engine:8091";
    private static final String GENETIC = "http://genetic-engine:8093";
    private static final String QUANTUM = "http://quantum-engine:8095";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final HealthStatusCache cache = new HealthStatusCache(new HealthCacheProperties(), clock);

    @Test
    void withoutEntryThereIsNoCachedStatus() {
        assertThat(cache.get(GREEDY)).isEmpty();
    }

    @Test
    void upIsServedUntilThePositiveTtlExpires() {
        cache.put(GREEDY, SolverStatus.UP);

        clock.advance(Duration.ofSeconds(29));
        assertThat(cache.get(GREEDY)).contains(SolverStatus.UP);

        clock.advance(Duration.ofSeconds(2));
        assertThat(cache.get(GREEDY)).isEmpty();
    }

    @Test
    void downExpiresSoonerThanUp() {
        cache.put(GREEDY, SolverStatus.DOWN);

        clock.advance(Duration.ofSeconds(4));
        assertThat(cache.get(GREEDY)).contains(SolverStatus.DOWN);

        clock.advance(Duration.ofSeconds(2));
        assertThat(cache.get(GREEDY)).isEmpty();
    }

    @Test
    void expiredEntriesAreDiscardedOnRead() {
        cache.put(GENETIC, SolverStatus.DOWN);
        clock.advance(Duration.ofSeconds(6));

        cache.get(GENETIC);

        assertThat(cache.get(GENETIC)).isEmpty();
    }

    @Test
    void unknownStatusIsNotCached() {
        cache.put(GENETIC, SolverStatus.UNKNOWN);

        assertThat(cache.get(GENETIC)).isEmpty();
    }

    @Test
    void invalidatingForcesTheNextProbe() {
        cache.put(QUANTUM, SolverStatus.UP);

        cache.invalidate(QUANTUM);

        assertThat(cache.get(QUANTUM)).isEmpty();
    }

    @Test
    void eachEngineHasItsOwnEntry() {
        cache.put(GREEDY, SolverStatus.UP);
        cache.put(GENETIC, SolverStatus.DOWN);

        assertThat(cache.get(GREEDY)).contains(SolverStatus.UP);
        assertThat(cache.get(GENETIC)).contains(SolverStatus.DOWN);
        assertThat(cache.get(QUANTUM)).isEmpty();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

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
            return instant;
        }
    }
}
