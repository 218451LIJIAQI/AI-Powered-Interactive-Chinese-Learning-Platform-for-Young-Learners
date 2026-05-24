package com.example.aichinese.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
 * <p>Records that have been idle for longer than the lockout window are
 * removed eagerly during normal access so the map cannot grow unbounded.
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

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxTrackedAttemptRecords;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this(clock, MAX_TRACKED_ATTEMPT_RECORDS);
    }

    LoginAttemptLimiter(Clock clock, int maxTrackedAttemptRecords) {
        if (maxTrackedAttemptRecords < 1) {
            throw new IllegalArgumentException("maxTrackedAttemptRecords must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxTrackedAttemptRecords = maxTrackedAttemptRecords;
    }

    /**
     * Returns {@code true} if the supplied (email, IP) pair is currently
     * allowed to attempt a login. The record is GC'd if its window has
     * expired, so callers do not need a separate cleanup task.
     */
    public boolean isAllowed(String email, String clientIp) {
        String key = key(email, clientIp);
        AttemptRecord record = attempts.get(key);
        if (record == null) {
            return true;
        }

        Instant now = clock.instant();
        if (record.lockedUntil != null) {
            if (record.lockedUntil.isAfter(now)) {
                return false;
            }
            // Lock window has elapsed; clear the slot so the user gets a fresh
            // attempt budget from this point onwards. Use the two-arg remove so a
            // concurrent failure update is not silently discarded.
            attempts.remove(key, record);
            return true;
        }

        // Drop stale records whose window has elapsed without ever locking;
        // keeps the map size bounded in long-running deployments.
        if (record.firstFailureAt.plus(ATTEMPT_WINDOW).isBefore(now)) {
            attempts.remove(key, record);
        }
        return true;
    }

    /**
     * Returns how long the caller must wait before the slot is allowed again.
     * Returns {@link Duration#ZERO} when the slot is not locked.
     */
    public Duration retryAfter(String email, String clientIp) {
        AttemptRecord record = attempts.get(key(email, clientIp));
        if (record == null || record.lockedUntil == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(clock.instant(), record.lockedUntil);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Records a failed attempt and locks the slot if the threshold is reached.
     * Returns {@code true} if the slot is now locked (i.e. the caller has been
     * locked out as a result of this exact failure).
     */
    public boolean recordFailure(String email, String clientIp) {
        String key = key(email, clientIp);
        Instant now = clock.instant();
        removeExpiredAttempts(now);

        AttemptRecord updated = attempts.compute(key, (ignored, existing) -> {
            if (existing == null || existing.firstFailureAt.plus(ATTEMPT_WINDOW).isBefore(now)) {
                return new AttemptRecord(now, 1, null, now);
            }
            int nextCount = existing.failedAttempts + 1;
            if (nextCount >= MAX_FAILED_ATTEMPTS) {
                return new AttemptRecord(existing.firstFailureAt, nextCount, now.plus(LOCK_DURATION), now);
            }
            return new AttemptRecord(existing.firstFailureAt, nextCount, null, now);
        });
        enforceCapacity();

        boolean nowLocked = updated.lockedUntil != null;
        if (nowLocked) {
            log.warn(
                    "Login lockout engaged for key={} after {} failed attempts; locked until {}.",
                    anonymizedKey(email, clientIp),
                    updated.failedAttempts,
                    updated.lockedUntil);
        }
        return nowLocked;
    }

    /**
     * Clears any failure record for the supplied pair. Called after a
     * successful login so legitimate users are not penalised for past
     * mistyped passwords once they get the credentials right.
     */
    public void recordSuccess(String email, String clientIp) {
        attempts.remove(key(email, clientIp));
    }

    int trackedAttemptCount() {
        return attempts.size();
    }

    private void removeExpiredAttempts(Instant now) {
        attempts.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    private boolean isExpired(AttemptRecord record, Instant now) {
        if (record.lockedUntil != null) {
            return !record.lockedUntil.isAfter(now);
        }
        return record.firstFailureAt.plus(ATTEMPT_WINDOW).isBefore(now);
    }

    private void enforceCapacity() {
        while (attempts.size() > maxTrackedAttemptRecords) {
            if (!evictOldestAttempt(clock.instant())) {
                return;
            }
        }
    }

    private boolean evictOldestAttempt(Instant now) {
        String candidateKey = null;
        AttemptRecord candidateRecord = null;
        boolean candidateLocked = true;
        for (Map.Entry<String, AttemptRecord> entry : attempts.entrySet()) {
            AttemptRecord currentRecord = entry.getValue();
            boolean currentLocked = currentRecord.lockedUntil != null && currentRecord.lockedUntil.isAfter(now);
            if (candidateRecord == null
                    || (candidateLocked && !currentLocked)
                    || (candidateLocked == currentLocked && currentRecord.lastUpdatedAt.isBefore(candidateRecord.lastUpdatedAt))) {
                candidateKey = entry.getKey();
                candidateRecord = currentRecord;
                candidateLocked = currentLocked;
            }
        }
        return candidateKey != null && attempts.remove(candidateKey, candidateRecord);
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

    private record AttemptRecord(Instant firstFailureAt, int failedAttempts, Instant lockedUntil, Instant lastUpdatedAt) {
    }
}
