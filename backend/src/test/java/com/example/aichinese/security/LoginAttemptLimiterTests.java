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
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String OTHER_IP = "198.51.100.42";
    private static final String MIXED_CASE_EMAIL = "Mixed.Case@Example.com";
    private static final String LOWERCASE_EMAIL = "mixed.case@example.com";
    private static final String UPPERCASE_EMAIL = "MIXED.CASE@EXAMPLE.COM";
    private static final String LONG_EMAIL_PREFIX = "a".repeat(400);
    private static final String LONG_IP_PREFIX = "1".repeat(100);
    private static final Instant START_TIME = Instant.parse("2025-01-01T00:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Duration DEFAULT_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(15);
    private static final int SMALL_CAPACITY = 3;
    private static final int CUSTOM_CAPACITY = 10;
    private static final int SPRAY_ATTEMPTS = 20;
    private static final int LOCKOUT_SPRAY_ATTEMPTS = 30;

    /** SDD 3.3: five failed sign-in attempts lock sign-in for 15 minutes. */
    @Test
    void productionDefaultsMatchTheDocumentedLockoutPolicy() {
        assertThat(LoginAttemptLimiter.MAX_FAILED_ATTEMPTS).isEqualTo(5);
        assertThat(LoginAttemptLimiter.ATTEMPT_WINDOW).isEqualTo(DEFAULT_ATTEMPT_WINDOW);
        assertThat(LoginAttemptLimiter.LOCK_DURATION).isEqualTo(DEFAULT_LOCK_DURATION);
    }

    @Test
    void allowsLoginWhenNoFailuresRecorded() {
        LoginAttemptLimiter limiter = limiter();

        assertThat(limiter.isAllowed(EMAIL, IP)).isTrue();
        assertThat(limiter.retryAfter(EMAIL, IP)).isEqualTo(Duration.ZERO);
    }

    @Test
    void locksAfterReachingFailureThreshold() {
        LoginAttemptLimiter limiter = limiter();

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
        MutableClock clock = mutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
        recordFailuresUntilLocked(limiter, EMAIL, IP);
        assertThat(limiter.isAllowed(EMAIL, IP)).isFalse();

        // Advance the clock past the lock window; the slot should reset.
        clock.advance(LoginAttemptLimiter.LOCK_DURATION.plusSeconds(1));

        assertThat(limiter.isAllowed(EMAIL, IP)).isTrue();
        assertThat(limiter.retryAfter(EMAIL, IP)).isEqualTo(Duration.ZERO);
    }

    @Test
    void successClearsAccumulatedFailures() {
        LoginAttemptLimiter limiter = limiter();

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
        LoginAttemptLimiter limiter = limiter();
        recordFailuresUntilLocked(limiter, EMAIL, IP);

        // Both axes (email, IP) need to differ to be independent. Using a
        // fresh email or fresh IP must not lock the unrelated bucket.
        assertThat(limiter.isAllowed(OTHER_EMAIL, IP)).isTrue();
        assertThat(limiter.isAllowed(EMAIL, OTHER_IP)).isTrue();
    }

    @Test
    void emailMatchingIsCaseInsensitive() {
        LoginAttemptLimiter limiter = limiter();
        recordFailuresUntilLocked(limiter, MIXED_CASE_EMAIL, IP);

        // The login pipeline normalises emails to lowercase before reaching
        // the limiter, but defence in depth: ensure the limiter itself is
        // not fooled by the case of the supplied address.
        assertThat(limiter.isAllowed(LOWERCASE_EMAIL, IP)).isFalse();
        assertThat(limiter.isAllowed(UPPERCASE_EMAIL, IP)).isFalse();
    }

    @Test
    void trackedAttemptRecordsStayWithinCapacity() {
        MutableClock clock = mutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, SMALL_CAPACITY);

        recordFailureSpray(limiter, clock, SPRAY_ATTEMPTS, "203.0.113.");

        assertThat(limiter.trackedAttemptCount()).isLessThanOrEqualTo(SMALL_CAPACITY);
    }

    @Test
    void activeLockoutIsNotEvictedByFreshFailureKeySpray() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC(), SMALL_CAPACITY);
        recordFailuresUntilLocked(limiter, EMAIL, IP);

        recordFailureSpray(limiter, null, LOCKOUT_SPRAY_ATTEMPTS, "198.51.100.");

        assertThat(limiter.isAllowed(EMAIL, IP)).isFalse();
        assertThat(limiter.retryAfter(EMAIL, IP)).isPositive();
    }

    @Test
    void configurableThresholdOfOneLocksOnFirstFailure() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                Clock.systemUTC(),
                1,
                DEFAULT_ATTEMPT_WINDOW,
                DEFAULT_LOCK_DURATION,
                CUSTOM_CAPACITY);

        assertThat(limiter.recordFailure(EMAIL, IP)).isTrue();
        assertThat(limiter.isAllowed(EMAIL, IP)).isFalse();
    }

    @Test
    void oversizedLimiterKeysAreNormalisedToBoundedEntries() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC(), CUSTOM_CAPACITY);

        limiter.recordFailure(LONG_EMAIL_PREFIX + "first@example.com", LONG_IP_PREFIX + "1");
        limiter.recordFailure(LONG_EMAIL_PREFIX + "second@example.com", LONG_IP_PREFIX + "2");

        assertThat(limiter.trackedAttemptCount()).isEqualTo(1);
    }

    private static LoginAttemptLimiter limiter() {
        return new LoginAttemptLimiter(Clock.systemUTC());
    }

    private static MutableClock mutableClock() {
        return new MutableClock(START_TIME);
    }

    private static void recordFailuresUntilLocked(LoginAttemptLimiter limiter, String email, String ip) {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(email, ip);
        }
    }

    private static void recordFailureSpray(
            LoginAttemptLimiter limiter,
            MutableClock clock,
            int attempts,
            String ipPrefix) {
        for (int i = 0; i < attempts; i++) {
            limiter.recordFailure("spray-" + i + "@example.com", ipPrefix + i);
            if (clock != null) {
                clock.advance(Duration.ofSeconds(1));
            }
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        private MutableClock(Instant initialInstant) {
            this.now = new AtomicReference<>(initialInstant);
        }

        @Override
        public ZoneId getZone() {
            return UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        private void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }
    }
}
