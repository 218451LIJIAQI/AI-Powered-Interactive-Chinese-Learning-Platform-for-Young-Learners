package com.example.aichinese.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Lightweight, in-memory rate limiter for the login endpoint.
 *
 * <p>The platform is deployed as a single-node application rather than a multi-replica service in
 * production, so we deliberately use a process-local map instead of pulling in
 * Redis or Bucket4j. The goal is to make trivial credential-stuffing /
 * brute-force attempts noticeably slower without introducing an external
 * dependency or a dedicated infrastructure service.
 *
 * <h2>Behaviour</h2>
 * <ul>
 *   <li>Each (email, client-IP) pair has an independent counter so a single
 *       compromised IP cannot lock out every user account.</li>
 *   <li>Failed login attempts increment the counter. Once a threshold is
 *       reached, the slot is locked for a fixed cool-down window and any
 *       further login attempts on that pair short-circuit with HTTP 429
 *       before the password is even checked.</li>
 *   <li>A successful login clears the counter for the offending pair so the
 *       legitimate owner is not punished for past mistakes.</li>
 * </ul>
 *
 * <p>Failure counters and active lockouts are held in separate bounded Caffeine
 * caches. Keeping active lockouts separate prevents a spray of fresh, one-off
 * failure keys from evicting an already locked slot, while avoiding repeated
 * full-map capacity scans.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /** Maximum failed attempts before the slot is locked. */
    static final int MAX_FAILED_ATTEMPTS = 5;

    /** Sliding window in which failed attempts accumulate. */
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    /** How long a slot stays locked once the threshold is breached. */
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    static final int MAX_TRACKED_ATTEMPT_RECORDS = 10_000;
    private static final int MAX_EMAIL_KEY_LENGTH = 320;
    private static final int MAX_IP_KEY_LENGTH = 64;

    private final Cache<String, AttemptRecord> attempts;
    private final Cache<String, Instant> lockouts;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration attemptWindow;
    private final Duration lockDuration;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    @Autowired
    public LoginAttemptLimiter(
            @Value("${app.security.login-rate-limit.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.login-rate-limit.attempt-window-minutes:15}") long attemptWindowMinutes,
            @Value("${app.security.login-rate-limit.lock-duration-minutes:15}") long lockDurationMinutes,
            @Value("${app.security.login-rate-limit.max-tracked-records:10000}") int maxTrackedAttemptRecords) {
        this(
                Clock.systemUTC(),
                maxFailedAttempts,
                Duration.ofMinutes(attemptWindowMinutes),
                Duration.ofMinutes(lockDurationMinutes),
                maxTrackedAttemptRecords);
    }

    LoginAttemptLimiter(Clock clock) {
        this(clock, MAX_TRACKED_ATTEMPT_RECORDS);
    }

    LoginAttemptLimiter(Clock clock, int maxTrackedAttemptRecords) {
        this(clock, MAX_FAILED_ATTEMPTS, ATTEMPT_WINDOW, LOCK_DURATION, maxTrackedAttemptRecords);
    }

    LoginAttemptLimiter(
            Clock clock,
            int maxFailedAttempts,
            Duration attemptWindow,
            Duration lockDuration,
            int maxTrackedAttemptRecords) {
        if (maxFailedAttempts < 1
                || attemptWindow == null || attemptWindow.isZero() || attemptWindow.isNegative()
                || lockDuration == null || lockDuration.isZero() || lockDuration.isNegative()
                || maxTrackedAttemptRecords < 1) {
            throw new IllegalArgumentException("Login rate limit settings must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxFailedAttempts = maxFailedAttempts;
        this.attemptWindow = attemptWindow;
        this.lockDuration = lockDuration;
        this.attempts = Caffeine.newBuilder()
                .maximumSize(maxTrackedAttemptRecords)
                .expireAfterAccess(attemptWindow)
                .build();
        this.lockouts = Caffeine.newBuilder()
                .maximumSize(maxTrackedAttemptRecords)
                .expireAfterWrite(lockDuration)
                .build();
    }

    /**
     * Returns {@code true} if the supplied (email, IP) pair is currently
     * allowed to attempt a login. The record is GC'd if its window has
     * expired, so callers do not need a separate cleanup task.
     */
    public boolean isAllowed(String email, String clientIp) {
        String key = key(email, clientIp);
        Instant now = clock.instant();
        Instant lockedUntil = lockouts.getIfPresent(key);
        if (lockedUntil != null) {
            if (lockedUntil.isAfter(now)) {
                return false;
            }
            lockouts.invalidate(key);
            return true;
        }

        AttemptRecord record = attempts.getIfPresent(key);
        if (record == null) {
            return true;
        }

        // Drop stale records whose window has elapsed without ever locking;
        // keeps the map size bounded in long-running deployments.
        if (!record.firstFailureAt.plus(attemptWindow).isAfter(now)) {
            attempts.asMap().remove(key, record);
        }
        return true;
    }

    /**
     * Returns how long the caller must wait before the slot is allowed again.
     * Returns {@link Duration#ZERO} when the slot is not locked.
     */
    public Duration retryAfter(String email, String clientIp) {
        String key = key(email, clientIp);
        Instant lockedUntil = lockouts.getIfPresent(key);
        if (lockedUntil == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(clock.instant(), lockedUntil);
        if (remaining.isZero() || remaining.isNegative()) {
            lockouts.invalidate(key);
            return Duration.ZERO;
        }
        return remaining;
    }

    /**
     * Records a failed attempt and locks the slot if the threshold is reached.
     * Returns {@code true} if the slot is now locked (i.e. the caller has been
     * locked out as a result of this exact failure).
     */
    public boolean recordFailure(String email, String clientIp) {
        String key = key(email, clientIp);
        Instant now = clock.instant();
        Instant existingLockout = lockouts.getIfPresent(key);
        if (existingLockout != null && existingLockout.isAfter(now)) {
            return true;
        }

        AttemptRecord updated = attempts.asMap().compute(key, (ignored, existing) -> {
            boolean freshWindow = existing == null || !existing.firstFailureAt.plus(attemptWindow).isAfter(now);
            Instant firstFailureAt = freshWindow ? now : existing.firstFailureAt;
            int nextCount = freshWindow ? 1 : existing.failedAttempts + 1;
            return new AttemptRecord(firstFailureAt, nextCount);
        });
        attempts.cleanUp();

        boolean nowLocked = updated.failedAttempts >= maxFailedAttempts;
        if (nowLocked) {
            Instant lockedUntil = now.plus(lockDuration);
            lockouts.put(key, lockedUntil);
            attempts.invalidate(key);
            log.warn(
                    "Login lockout engaged for key={} after {} failed attempts; locked until {}.",
                    anonymizedKey(email, clientIp),
                    updated.failedAttempts,
                    lockedUntil);
        }
        return nowLocked;
    }

    /**
     * Clears any failure record for the supplied pair. Called after a
     * successful login so legitimate users are not penalised for past
     * mistyped passwords once they get the credentials right.
     */
    public void recordSuccess(String email, String clientIp) {
        String key = key(email, clientIp);
        attempts.invalidate(key);
        lockouts.invalidate(key);
    }

    int trackedAttemptCount() {
        attempts.cleanUp();
        lockouts.cleanUp();
        return Math.toIntExact(attempts.estimatedSize() + lockouts.estimatedSize());
    }

    private String key(String email, String clientIp) {
        // strip() (vs trim()) is Unicode-aware, so a stray CJK fullwidth space
        // before the email cannot produce a different bucket than the
        // normalised form the login pipeline submits.
        String normalisedEmail = truncate(
                email == null ? "" : email.strip().toLowerCase(Locale.ROOT), MAX_EMAIL_KEY_LENGTH);
        String normalisedIp = truncate(
                clientIp == null ? "" : clientIp.strip(), MAX_IP_KEY_LENGTH);
        return normalisedEmail + "|" + normalisedIp;
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

    private String anonymizedKey(String email, String clientIp) {
        return HEX_FORMAT.toHexDigits(key(email, clientIp).hashCode());
    }

    private record AttemptRecord(Instant firstFailureAt, int failedAttempts) {
    }
}
