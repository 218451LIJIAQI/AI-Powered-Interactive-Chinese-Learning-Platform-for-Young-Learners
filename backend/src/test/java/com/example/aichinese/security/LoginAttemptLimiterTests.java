package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.InstantSource;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LoginAttemptLimiter}. The limiter is the
 * brute-force defence in front of the login endpoint, so it has its own
 * dedicated suite that exercises the exact boundary conditions (threshold,
 * lock window expiry, success-clears-counter, attempt window expiry).
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
    private static final int SMALL_CAPACITY = 3;
    private static final int CUSTOM_CAPACITY = 10;

    @Test
    void allowsLoginWhenNoFailuresRecorded() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        assertThat(isAllowed(limiter, EMAIL, IP)).isTrue();
        assertThat(limiter.retryAfter(EMAIL, IP)).isEqualTo(Duration.ZERO);
    }

    @Test
    void locksAfterReachingFailureThreshold() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
            boolean nowLocked = limiter.recordFailure(EMAIL, IP);
            assertThat(nowLocked).as("attempt %d", i + 1).isFalse();
            assertThat(isAllowed(limiter, EMAIL, IP)).isTrue();
        }

        boolean lockedOnLastAttempt = limiter.recordFailure(EMAIL, IP);

        assertThat(lockedOnLastAttempt).isTrue();
        assertThat(limiter.recordFailure(EMAIL, IP))
                .as("subsequent failures while already locked do not re-engage lockout")
                .isFalse();
        assertThat(isAllowed(limiter, EMAIL, IP)).isFalse();
        assertThat(limiter.retryAfter(EMAIL, IP)).isPositive();
    }

    @Test
    void unlocksOnceLockWindowElapses() {
        MutableInstantSource clock = new MutableInstantSource();
        LoginAttemptLimiter limiter = limiter(clock);
        recordFailuresUntilLocked(limiter, EMAIL, IP);
        assertThat(isAllowed(limiter, EMAIL, IP)).isFalse();

        // Advance the clock past the lock window; the slot should reset.
        clock.advance(LoginAttemptLimiter.LOCK_DURATION.plusSeconds(1));

        assertThat(isAllowed(limiter, EMAIL, IP)).isTrue();
        assertThat(limiter.retryAfter(EMAIL, IP)).isEqualTo(Duration.ZERO);
    }

    @Test
    void failuresOlderThanTheAttemptWindowStopCounting() {
        // Just inside the 15-minute window the earlier failures still count.
        MutableInstantSource insideClock = new MutableInstantSource();
        LoginAttemptLimiter insideLimiter = limiter(insideClock);
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
            insideLimiter.recordFailure(EMAIL, IP);
        }
        insideClock.advance(LoginAttemptLimiter.ATTEMPT_WINDOW.minusSeconds(1));
        assertThat(insideLimiter.recordFailure(EMAIL, IP)).as("fifth failure inside the window").isTrue();

        // The window runs from the first failure: once it has passed, the next failure starts a new count.
        MutableInstantSource clock = new MutableInstantSource();
        LoginAttemptLimiter limiter = limiter(clock);
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
            limiter.recordFailure(EMAIL, IP);
        }
        clock.advance(LoginAttemptLimiter.ATTEMPT_WINDOW);

        assertThat(limiter.recordFailure(EMAIL, IP)).as("earlier failures no longer count").isFalse();
        assertThat(isAllowed(limiter, EMAIL, IP)).isTrue();
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS - 2; i++) {
            assertThat(limiter.recordFailure(EMAIL, IP)).isFalse();
        }
        assertThat(limiter.recordFailure(EMAIL, IP)).as("five failures inside the new window").isTrue();
    }

    @Test
    void successClearsAccumulatedFailures() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

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
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        recordFailuresUntilLocked(limiter, EMAIL, IP);

        // A bucket is keyed by the email and the IP together, so changing
        // either one alone gives an independent bucket that is not locked.
        assertThat(isAllowed(limiter, OTHER_EMAIL, IP)).isTrue();
        assertThat(isAllowed(limiter, EMAIL, OTHER_IP)).isTrue();
    }

    @Test
    void emailMatchingIsCaseInsensitive() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        recordFailuresUntilLocked(limiter, MIXED_CASE_EMAIL, IP);

        // The login pipeline normalises emails to lowercase before reaching
        // the limiter, but defence in depth: ensure the limiter itself is
        // not fooled by the case of the supplied address.
        assertThat(isAllowed(limiter, LOWERCASE_EMAIL, IP)).isFalse();
        assertThat(isAllowed(limiter, UPPERCASE_EMAIL, IP)).isFalse();
    }

    @Test
    void trackedAttemptRecordsStayWithinCapacity() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(InstantSource.system(), SMALL_CAPACITY);

        recordFailureSpray(limiter);

        assertThat(limiter.trackedAttemptCount()).isLessThanOrEqualTo(SMALL_CAPACITY);
    }

    @Test
    void activeLockoutIsNotEvictedByFreshFailureKeySpray() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(InstantSource.system(), SMALL_CAPACITY);
        recordFailuresUntilLocked(limiter, EMAIL, IP);

        recordFailureSpray(limiter);

        assertThat(isAllowed(limiter, EMAIL, IP)).isFalse();
        assertThat(limiter.retryAfter(EMAIL, IP)).isPositive();
    }

    /** SDD 3.4: five failed sign-ins inside 15 minutes lock that e-mail and address for 15 minutes. */
    @Test
    void usesTheDocumentedPolicy() {
        assertThat(LoginAttemptLimiter.MAX_FAILED_ATTEMPTS).isEqualTo(5);
        assertThat(LoginAttemptLimiter.ATTEMPT_WINDOW).isEqualTo(Duration.ofMinutes(15));
        assertThat(LoginAttemptLimiter.LOCK_DURATION).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void oversizedLimiterKeysAreNormalisedToBoundedEntries() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(InstantSource.system(), CUSTOM_CAPACITY);

        limiter.recordFailure(LONG_EMAIL_PREFIX + "first@example.com", LONG_IP_PREFIX + "1");
        limiter.recordFailure(LONG_EMAIL_PREFIX + "second@example.com", LONG_IP_PREFIX + "2");

        assertThat(limiter.trackedAttemptCount()).isEqualTo(1);
    }

    private static boolean isAllowed(LoginAttemptLimiter limiter, String email, String ip) {
        return limiter.retryAfter(email, ip).isZero();
    }

    private static LoginAttemptLimiter limiter(InstantSource clock) {
        return new LoginAttemptLimiter(clock, LoginAttemptLimiter.MAX_TRACKED_ATTEMPT_RECORDS);
    }

    private static void recordFailuresUntilLocked(LoginAttemptLimiter limiter, String email, String ip) {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure(email, ip);
        }
    }

    /** One failure each for far more fresh keys than {@link #SMALL_CAPACITY}. */
    private static void recordFailureSpray(LoginAttemptLimiter limiter) {
        for (int i = 0; i < 30; i++) {
            limiter.recordFailure("spray-" + i + "@example.com", "198.51.100." + i);
        }
    }
}
