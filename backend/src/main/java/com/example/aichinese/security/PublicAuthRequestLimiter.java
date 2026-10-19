package com.example.aichinese.security;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.util.TextUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class PublicAuthRequestLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_SUBJECT_KEY_LENGTH = 320;
    private static final int MAX_IP_KEY_LENGTH = 64;

    private final Cache<String, RequestRecord> requests;
    private final Clock clock;
    private final Duration window;
    private final int maxRegisterRequests;
    private final int maxForgotPasswordRequests;

    @Autowired
    public PublicAuthRequestLimiter(
            @Value("${app.security.public-auth-rate-limit.register-requests:100}") int maxRegisterRequests,
            @Value("${app.security.public-auth-rate-limit.forgot-password-requests:10}") int maxForgotPasswordRequests,
            @Value("${app.security.public-auth-rate-limit.max-tracked-records:10000}") int maxTrackedRequestRecords,
            @Value("${app.security.public-auth-rate-limit.window-minutes:15}") long windowMinutes) {
        this(
                Clock.systemUTC(),
                Duration.ofMinutes(windowMinutes),
                maxRegisterRequests,
                maxForgotPasswordRequests,
                maxTrackedRequestRecords);
    }

    PublicAuthRequestLimiter(
            Clock clock,
            int maxRegisterRequests,
            int maxForgotPasswordRequests,
            int maxTrackedRequestRecords) {
        this(clock, WINDOW, maxRegisterRequests, maxForgotPasswordRequests, maxTrackedRequestRecords);
    }

    PublicAuthRequestLimiter(
            Clock clock,
            Duration window,
            int maxRegisterRequests,
            int maxForgotPasswordRequests,
            int maxTrackedRequestRecords) {
        if (window == null || window.isZero() || window.isNegative()
                || maxRegisterRequests < 1 || maxForgotPasswordRequests < 1 || maxTrackedRequestRecords < 1) {
            throw new IllegalArgumentException("Rate limit thresholds must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.window = window;
        this.maxRegisterRequests = maxRegisterRequests;
        this.maxForgotPasswordRequests = maxForgotPasswordRequests;
        this.requests = Caffeine.newBuilder()
                .maximumSize(maxTrackedRequestRecords)
                .expireAfterAccess(window)
                .build();
    }

    public void checkRegisterAllowed(String clientIp) {
        checkAllowed("REGISTER_IP", "", clientIp, maxRegisterRequests);
    }

    public void checkForgotPasswordAllowed(String email, String clientIp) {
        checkAllowed("FORGOT_PASSWORD_IP", "", clientIp, maxForgotPasswordRequests);
        checkAllowed("FORGOT_PASSWORD_EMAIL", email, "", maxForgotPasswordRequests);
    }

    /** Reset-password submissions get their own per-IP bucket capped at the forgot-password limit (audit A1 §2.4). */
    public void checkResetPasswordAllowed(String clientIp) {
        checkAllowed("RESET_PASSWORD_IP", "", clientIp, maxForgotPasswordRequests);
    }

    int trackedRequestCount() {
        requests.cleanUp();
        return Math.toIntExact(requests.estimatedSize());
    }

    private void checkAllowed(String action, String subject, String clientIp, int maxRequests) {
        Duration retryAfter = recordAndGetRetryAfter(action, subject, clientIp, maxRequests);
        if (!retryAfter.isZero() && !retryAfter.isNegative()) {
            long retrySeconds = Math.max(1L, retryAfter.toSeconds());
            throw new ResponseStatusException(
                    TOO_MANY_REQUESTS,
                    "Too many requests. Please try again in " + retrySeconds + " seconds.");
        }
    }

    private Duration recordAndGetRetryAfter(String action, String subject, String clientIp, int maxRequests) {
        String key = key(action, subject, clientIp);
        Instant now = clock.instant();
        AtomicReference<Duration> retryAfter = new AtomicReference<>(Duration.ZERO);

        requests.asMap().compute(key, (ignored, existing) -> {
            if (existing == null || !existing.windowStartedAt.plus(window).isAfter(now)) {
                return new RequestRecord(now, 1);
            }
            if (existing.requestCount >= maxRequests) {
                retryAfter.set(Duration.between(now, existing.windowStartedAt.plus(window)));
                return existing;
            }
            return new RequestRecord(existing.windowStartedAt, existing.requestCount + 1);
        });
        requests.cleanUp();
        return retryAfter.get();
    }

    private String key(String action, String subject, String clientIp) {
        String normalizedSubject = TextUtils.safeTruncate(
                subject == null ? "" : subject.strip().toLowerCase(Locale.ROOT), MAX_SUBJECT_KEY_LENGTH);
        String normalizedIp = TextUtils.safeTruncate(
                clientIp == null ? "" : clientIp.strip(), MAX_IP_KEY_LENGTH);
        return action + "|" + normalizedSubject + "|" + normalizedIp;
    }

    private record RequestRecord(Instant windowStartedAt, int requestCount) {
    }
}
