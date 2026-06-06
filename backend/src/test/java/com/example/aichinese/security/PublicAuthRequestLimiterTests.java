package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit coverage for {@code PublicAuthRequestLimiter}. It pins the
 * separate quotas for register (IP-keyed) and forgot-password
 * (IP+email-keyed) flows so the limiter applies the right window
 * per surface, and the 429 rejection after exhaustion.
 */
class PublicAuthRequestLimiterTests {

    private static final String IP = "203.0.113.7";
    private static final String USER_EMAIL = "user@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String TARGET_EMAIL = "target@example.com";
    private static final String TARGET_IP_1 = "203.0.113.10";
    private static final String TARGET_IP_2 = "203.0.113.11";
    private static final String TARGET_IP_3 = "203.0.113.12";
    private static final String LONG_EMAIL_PREFIX = "a".repeat(400);
    private static final String LONG_IP_PREFIX = "1".repeat(100);
    private static final Instant START_TIME = Instant.parse("2025-01-01T00:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final int REGISTER_THRESHOLD = 3;
    private static final int FORGOT_PASSWORD_THRESHOLD = 2;
    private static final int SINGLE_REQUEST_THRESHOLD = 1;
    private static final int SMALL_CAPACITY = 3;
    private static final int DEFAULT_CAPACITY = 10;
    private static final int HIGH_THRESHOLD = 100;
    private static final int SPRAY_ATTEMPTS = 20;

    @Test
    void registerRequestsAreRejectedAfterThreshold() {
        PublicAuthRequestLimiter limiter = limiter(REGISTER_THRESHOLD, REGISTER_THRESHOLD, DEFAULT_CAPACITY);

        repeat(REGISTER_THRESHOLD, () -> limiter.checkRegisterAllowed(IP));

        assertTooManyRequests(() -> limiter.checkRegisterAllowed(IP));
    }

    @Test
    void forgotPasswordRequestsAreRejectedByIpAndEmailThresholds() {
        PublicAuthRequestLimiter limiter = limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);

        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkForgotPasswordAllowed(USER_EMAIL, IP));

        assertTooManyRequests(() -> limiter.checkForgotPasswordAllowed(OTHER_EMAIL, IP));

        PublicAuthRequestLimiter emailLimiter =
                limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);
        emailLimiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_1);
        emailLimiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_2);

        assertTooManyRequests(() -> emailLimiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_3));
    }

    @Test
    void requestRecordsExpireAfterWindow() {
        MutableClock clock = mutableClock();
        PublicAuthRequestLimiter limiter = limiter(
                clock,
                SINGLE_REQUEST_THRESHOLD,
                SINGLE_REQUEST_THRESHOLD,
                DEFAULT_CAPACITY);

        limiter.checkRegisterAllowed(IP);
        assertTooManyRequests(() -> limiter.checkRegisterAllowed(IP));

        clock.advance(PublicAuthRequestLimiter.WINDOW.plusSeconds(1));

        limiter.checkRegisterAllowed(IP);
        assertThat(limiter.trackedRequestCount()).isEqualTo(1);
    }

    @Test
    void requestBudgetResetsExactlyAtWindowBoundary() {
        MutableClock clock = mutableClock();
        PublicAuthRequestLimiter limiter = limiter(
                clock,
                SINGLE_REQUEST_THRESHOLD,
                SINGLE_REQUEST_THRESHOLD,
                DEFAULT_CAPACITY);

        limiter.checkRegisterAllowed(IP);
        clock.advance(PublicAuthRequestLimiter.WINDOW);

        limiter.checkRegisterAllowed(IP);
        assertTooManyRequests(() -> limiter.checkRegisterAllowed(IP));
    }

    @Test
    void trackedRequestRecordsStayWithinCapacity() {
        MutableClock clock = mutableClock();
        PublicAuthRequestLimiter limiter = limiter(clock, HIGH_THRESHOLD, HIGH_THRESHOLD, SMALL_CAPACITY);

        recordRegisterSpray(limiter, clock);

        assertThat(limiter.trackedRequestCount()).isLessThanOrEqualTo(SMALL_CAPACITY);
    }

    @Test
    void oversizedRequestKeysAreNormalisedToBoundedEntries() {
        PublicAuthRequestLimiter limiter = limiter(HIGH_THRESHOLD, HIGH_THRESHOLD, DEFAULT_CAPACITY);

        limiter.checkForgotPasswordAllowed(LONG_EMAIL_PREFIX + "first@example.com", LONG_IP_PREFIX + "1");
        limiter.checkForgotPasswordAllowed(LONG_EMAIL_PREFIX + "second@example.com", LONG_IP_PREFIX + "2");

        assertThat(limiter.trackedRequestCount()).isEqualTo(2);
    }

    private static PublicAuthRequestLimiter limiter(
            int maxRegisterRequests,
            int maxForgotPasswordRequests,
            int capacity) {
        return limiter(Clock.systemUTC(), maxRegisterRequests, maxForgotPasswordRequests, capacity);
    }

    private static PublicAuthRequestLimiter limiter(
            Clock clock,
            int maxRegisterRequests,
            int maxForgotPasswordRequests,
            int capacity) {
        return new PublicAuthRequestLimiter(clock, maxRegisterRequests, maxForgotPasswordRequests, capacity);
    }

    private static MutableClock mutableClock() {
        return new MutableClock(START_TIME);
    }

    private static void assertTooManyRequests(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(TOO_MANY_REQUESTS));
    }

    private static void repeat(int times, Runnable action) {
        for (int i = 0; i < times; i++) {
            action.run();
        }
    }

    private static void recordRegisterSpray(PublicAuthRequestLimiter limiter, MutableClock clock) {
        for (int i = 0; i < SPRAY_ATTEMPTS; i++) {
            limiter.checkRegisterAllowed("203.0.113." + i);
            clock.advanceSeconds(1);
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

        private void advanceSeconds(long seconds) {
            now.updateAndGet(instant -> instant.plusSeconds(seconds));
        }
    }
}
