package com.example.aichinese.security;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-memory rate limiter for the anonymous-accessible authentication
 * endpoints ({@code /api/auth/register},
 * {@code /api/auth/forgot-password}).
 *
 * <h2>Why two separate caps</h2>
 * <p>Registration and forgot-password both expose user-enumeration
 * surfaces (they reveal whether an email is already in use, or trigger
 * an external email send) but they have different abuse models:</p>
 * <ul>
 *   <li><b>Register</b> is bot-farmed for free signups → keyed only by
 *       IP, with a generous {@value #MAX_REGISTER_REQUESTS}-per-window
 *       cap so a school computer lab doesn't accidentally lock itself
 *       out.</li>
 *   <li><b>Forgot-password</b> is used both for credential-stuffing
 *       (probe one email per request) and for email bombing (force the
 *       SMTP service to spam one victim) → keyed by both IP <em>and</em>
 *       email so neither vector accumulates without limit, with a
 *       tighter {@value #MAX_FORGOT_PASSWORD_REQUESTS}-per-window cap.</li>
 * </ul>
 *
 * <h2>State management</h2>
 * <p>State is held in a process-local {@link ConcurrentHashMap},
 * matching the single-replica deployment assumption documented on
 * {@link LoginAttemptLimiter}. Expired records are evicted during
 * normal access; a hard cap on tracked entries
 * ({@value #MAX_TRACKED_REQUEST_RECORDS}) is enforced via LRU eviction
 * to bound memory under sustained abuse.</p>
 */
@Component
public class PublicAuthRequestLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_REGISTER_REQUESTS = 100;
    static final int MAX_FORGOT_PASSWORD_REQUESTS = 10;
    static final int MAX_TRACKED_REQUEST_RECORDS = 10_000;
    private static final int MAX_SUBJECT_KEY_LENGTH = 320;
    private static final int MAX_IP_KEY_LENGTH = 64;

    private final Map<String, RequestRecord> requests = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxRegisterRequests;
    private final int maxForgotPasswordRequests;
    private final int maxTrackedRequestRecords;

    public PublicAuthRequestLimiter() {
        this(Clock.systemUTC(), MAX_REGISTER_REQUESTS, MAX_FORGOT_PASSWORD_REQUESTS, MAX_TRACKED_REQUEST_RECORDS);
    }

    PublicAuthRequestLimiter(
            Clock clock,
            int maxRegisterRequests,
            int maxForgotPasswordRequests,
            int maxTrackedRequestRecords) {
        if (maxRegisterRequests < 1 || maxForgotPasswordRequests < 1 || maxTrackedRequestRecords < 1) {
            throw new IllegalArgumentException("Rate limit thresholds must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxRegisterRequests = maxRegisterRequests;
        this.maxForgotPasswordRequests = maxForgotPasswordRequests;
        this.maxTrackedRequestRecords = maxTrackedRequestRecords;
    }

    public void checkRegisterAllowed(String clientIp) {
        checkAllowed("REGISTER_IP", "", clientIp, maxRegisterRequests);
    }

    public void checkForgotPasswordAllowed(String email, String clientIp) {
        checkAllowed("FORGOT_PASSWORD_IP", "", clientIp, maxForgotPasswordRequests);
        checkAllowed("FORGOT_PASSWORD_EMAIL", email, "", maxForgotPasswordRequests);
    }

    int trackedRequestCount() {
        return requests.size();
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
        removeExpiredRequests(now);
        AtomicReference<Duration> retryAfter = new AtomicReference<>(Duration.ZERO);

        requests.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStartedAt.plus(WINDOW).isBefore(now)) {
                return new RequestRecord(now, 1, now);
            }
            if (existing.requestCount >= maxRequests) {
                retryAfter.set(Duration.between(now, existing.windowStartedAt.plus(WINDOW)));
                return new RequestRecord(existing.windowStartedAt, existing.requestCount, now);
            }
            return new RequestRecord(existing.windowStartedAt, existing.requestCount + 1, now);
        });
        enforceCapacity();
        return retryAfter.get();
    }

    private void removeExpiredRequests(Instant now) {
        requests.entrySet().removeIf(entry -> !entry.getValue().windowStartedAt.plus(WINDOW).isAfter(now));
    }

    private void enforceCapacity() {
        while (requests.size() > maxTrackedRequestRecords) {
            if (!evictOldestRequest()) {
                return;
            }
        }
    }

    private boolean evictOldestRequest() {
        String candidateKey = null;
        RequestRecord candidateRecord = null;
        for (Map.Entry<String, RequestRecord> entry : requests.entrySet()) {
            RequestRecord currentRecord = entry.getValue();
            if (candidateRecord == null || currentRecord.lastUpdatedAt.isBefore(candidateRecord.lastUpdatedAt)) {
                candidateKey = entry.getKey();
                candidateRecord = currentRecord;
            }
        }
        return candidateKey != null && requests.remove(candidateKey, candidateRecord);
    }

    private String key(String action, String subject, String clientIp) {
        // strip() (vs trim()) is Unicode-aware so a stray CJK fullwidth space
        // before the email cannot produce a different bucket than the
        // normalised form the auth pipeline submits.
        String normalizedSubject = truncate(
                subject == null ? "" : subject.strip().toLowerCase(Locale.ROOT), MAX_SUBJECT_KEY_LENGTH);
        String normalizedIp = truncate(
                clientIp == null ? "" : clientIp.strip(), MAX_IP_KEY_LENGTH);
        return action + "|" + normalizedSubject + "|" + normalizedIp;
    }

    /**
     * Truncates {@code value} to {@code maxLength} UTF-16 code units, taking
     * care not to split a surrogate pair in half. Email / IP keys should
     * never contain non-BMP characters, but we still guard against it so a
     * malformed input cannot produce an invalid Java String.
     */
    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength > 0 && Character.isHighSurrogate(value.charAt(maxLength - 1))) {
            return value.substring(0, maxLength - 1);
        }
        return value.substring(0, maxLength);
    }

    private record RequestRecord(Instant windowStartedAt, int requestCount, Instant lastUpdatedAt) {
    }
}
