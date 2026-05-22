package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PublicAuthRequestLimiterTests {

    private static final String IP = "203.0.113.7";

    @Test
    void registerRequestsAreRejectedAfterThreshold() {
        PublicAuthRequestLimiter limiter = new PublicAuthRequestLimiter(Clock.systemUTC(), 3, 3, 10);

        limiter.checkRegisterAllowed(IP);
        limiter.checkRegisterAllowed(IP);
        limiter.checkRegisterAllowed(IP);

        assertThatThrownBy(() -> limiter.checkRegisterAllowed(IP))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(TOO_MANY_REQUESTS));
    }

    @Test
    void forgotPasswordRequestsAreRejectedByIpAndEmailThresholds() {
        PublicAuthRequestLimiter limiter = new PublicAuthRequestLimiter(Clock.systemUTC(), 3, 2, 10);

        limiter.checkForgotPasswordAllowed("user@example.com", IP);
        limiter.checkForgotPasswordAllowed("user@example.com", IP);

        assertThatThrownBy(() -> limiter.checkForgotPasswordAllowed("other@example.com", IP))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(TOO_MANY_REQUESTS));

        PublicAuthRequestLimiter emailLimiter = new PublicAuthRequestLimiter(Clock.systemUTC(), 3, 2, 10);
        emailLimiter.checkForgotPasswordAllowed("target@example.com", "203.0.113.10");
        emailLimiter.checkForgotPasswordAllowed("target@example.com", "203.0.113.11");

        assertThatThrownBy(() -> emailLimiter.checkForgotPasswordAllowed("target@example.com", "203.0.113.12"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(TOO_MANY_REQUESTS));
    }

    @Test
    void requestRecordsExpireAfterWindow() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2025-01-01T00:00:00Z"));
        Clock movingClock = movingClock(now);
        PublicAuthRequestLimiter limiter = new PublicAuthRequestLimiter(movingClock, 1, 1, 10);

        limiter.checkRegisterAllowed(IP);
        assertThatThrownBy(() -> limiter.checkRegisterAllowed(IP)).isInstanceOf(ResponseStatusException.class);

        now.set(now.get().plus(PublicAuthRequestLimiter.WINDOW).plusSeconds(1));

        limiter.checkRegisterAllowed(IP);
        assertThat(limiter.trackedRequestCount()).isEqualTo(1);
    }

    @Test
    void trackedRequestRecordsStayWithinCapacity() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2025-01-01T00:00:00Z"));
        Clock movingClock = movingClock(now);
        PublicAuthRequestLimiter limiter = new PublicAuthRequestLimiter(movingClock, 100, 100, 3);

        for (int i = 0; i < 20; i++) {
            limiter.checkRegisterAllowed("203.0.113." + i);
            now.set(now.get().plusSeconds(1));
        }

        assertThat(limiter.trackedRequestCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void oversizedRequestKeysAreNormalisedToBoundedEntries() {
        PublicAuthRequestLimiter limiter = new PublicAuthRequestLimiter(Clock.systemUTC(), 100, 100, 10);
        String longEmailPrefix = "a".repeat(400);
        String longIpPrefix = "1".repeat(100);

        limiter.checkForgotPasswordAllowed(longEmailPrefix + "first@example.com", longIpPrefix + "1");
        limiter.checkForgotPasswordAllowed(longEmailPrefix + "second@example.com", longIpPrefix + "2");

        assertThat(limiter.trackedRequestCount()).isEqualTo(2);
    }

    private Clock movingClock(AtomicReference<Instant> now) {
        return new Clock() {
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
    }
}
