package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LoginAttemptLimiter}. The limiter is the new
 * brute-force defence in front of the login endpoint, so it has its own
 * dedicated suite that exercises the exact boundary conditions (threshold,
 * lock window expiry, success-clears-counter, sliding window).
 *
 * <p>Lives in the same package as the SUT so the package-private clock-based
 * constructor and constants can be exercised directly without forcing them
 * into the public API surface.</p>
 */
class LoginAttemptLimiterTests {

    private static final String EMAIL = "user@example.com";
    private static final String IP = "203.0.113.7";

    @Test
    void allowsLoginWhenNoFailuresRecorded() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());

        assertThat(limiter.isAllowed(EMAIL, IP)).isTrue();
        assertThat(limiter.retryAfter(EMAIL, IP)).isEqualTo(Duration.ZERO);
    }

    @Test
    void locksAfterReachingFailureThreshold() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());

        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
            boolean nowLocked = limiter.recordFailure(EMAIL, IP);
            assertThat(nowLocked).as("attempt %d", i + 1).isFalse();
            assertThat(limiter.isAllowed(EMAIL, IP)).isTrue();
        }

        boolean lockedOnLastAttempt = limiter.recordFailure(EMAIL, IP);

        assertThat(lockedOnLastAttempt).isTrue();
        assertThat(limiter.isAllowed(EMAIL, IP)).isFalse();
        assertThat(limiter.retryAfter(EMAIL, IP)).isPositive();
    }

    @Test
    void unlocksOnceLockWindowElapses() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2025-01-01T00:00:00Z"));
        Clock movingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };

        LoginAttemptLimiter limiter = new LoginAttemptLimiter(movingClock);
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(EMAIL, IP);
        }
        assertThat(limiter.isAllowed(EMAIL, IP)).isFalse();

        // Advance the clock past the lock window — the slot should reset.
        now.set(now.get().plus(LoginAttemptLimiter.LOCK_DURATION).plusSeconds(1));

        assertThat(limiter.isAllowed(EMAIL, IP)).isTrue();
        assertThat(limiter.retryAfter(EMAIL, IP)).isEqualTo(Duration.ZERO);
    }

    @Test
    void successClearsAccumulatedFailures() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());

        limiter.recordFailure(EMAIL, IP);
        limiter.recordFailure(EMAIL, IP);
        limiter.recordSuccess(EMAIL, IP);

        // After success the slot is empty, so the threshold counter starts
        // over; the user does not get punished for past mistyped passwords.
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThat(limiter.recordFailure(EMAIL, IP)).isFalse();
        }
    }

    @Test
    void differentEmailsAndIpsAreIndependent() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());

        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(EMAIL, IP);
        }

        // Both axes (email, IP) need to differ to be independent. Using a
        // fresh email or fresh IP must not lock the unrelated bucket.
        assertThat(limiter.isAllowed("other@example.com", IP)).isTrue();
        assertThat(limiter.isAllowed(EMAIL, "198.51.100.42")).isTrue();
    }

    @Test
    void emailMatchingIsCaseInsensitive() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure("Mixed.Case@Example.com", IP);
        }

        // The login pipeline normalises emails to lowercase before reaching
        // the limiter, but defence in depth: ensure the limiter itself is
        // not fooled by the case of the supplied address.
        assertThat(limiter.isAllowed("mixed.case@example.com", IP)).isFalse();
        assertThat(limiter.isAllowed("MIXED.CASE@EXAMPLE.COM", IP)).isFalse();
    }

    @Test
    void trackedAttemptRecordsStayWithinCapacity() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2025-01-01T00:00:00Z"));
        Clock movingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(movingClock, 3);

        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("spray-" + i + "@example.com", "203.0.113." + i);
            now.set(now.get().plusSeconds(1));
        }

        assertThat(limiter.trackedAttemptCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void activeLockoutIsNotEvictedByFreshFailureKeySpray() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC(), 3);
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(EMAIL, IP);
        }

        for (int i = 0; i < 30; i++) {
            limiter.recordFailure("spray-" + i + "@example.com", "198.51.100." + i);
        }

        assertThat(limiter.isAllowed(EMAIL, IP)).isFalse();
        assertThat(limiter.retryAfter(EMAIL, IP)).isPositive();
    }

    @Test
    void configurableThresholdOfOneLocksOnFirstFailure() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                Clock.systemUTC(), 1, Duration.ofMinutes(15), Duration.ofMinutes(15), 10);

        assertThat(limiter.recordFailure(EMAIL, IP)).isTrue();
        assertThat(limiter.isAllowed(EMAIL, IP)).isFalse();
    }

    @Test
    void oversizedLimiterKeysAreNormalisedToBoundedEntries() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC(), 10);
        String longEmailPrefix = "a".repeat(400);
        String longIpPrefix = "1".repeat(100);

        limiter.recordFailure(longEmailPrefix + "first@example.com", longIpPrefix + "1");
        limiter.recordFailure(longEmailPrefix + "second@example.com", longIpPrefix + "2");

        assertThat(limiter.trackedAttemptCount()).isEqualTo(1);
    }
}
