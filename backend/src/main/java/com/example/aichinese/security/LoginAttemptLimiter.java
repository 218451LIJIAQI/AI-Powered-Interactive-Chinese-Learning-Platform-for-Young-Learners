package com.example.aichinese.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lightweight, in-memory rate limiter for the login endpoint.
 *
 * <p>The platform is a university prototype that does not run multi-replica in
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

    /** Maximum failed attempts before the slot is locked. */
    static final int MAX_FAILED_ATTEMPTS = 5;

    /** Sliding window in which failed attempts accumulate. */
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    /** How long a slot stays locked once the threshold is breached. */
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
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
        if (record.lockedUntil != null && record.lockedUntil.isAfter(now)) {
            return false;
        }

        if (record.lockedUntil != null && !record.lockedUntil.isAfter(now)) {
            // Lock window has elapsed; clear the slot so the user gets a fresh
            // attempt budget from this point onwards.
            attempts.remove(key);
            return true;
        }

        // Drop stale records whose window has elapsed without ever locking;
        // keeps the map size bounded in long-running deployments.
        if (record.firstFailureAt.plus(ATTEMPT_WINDOW).isBefore(now)) {
            attempts.remove(key);
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

        AttemptRecord updated = attempts.compute(key, (ignored, existing) -> {
            if (existing == null || existing.firstFailureAt.plus(ATTEMPT_WINDOW).isBefore(now)) {
                return new AttemptRecord(now, 1, null);
            }
            int nextCount = existing.failedAttempts + 1;
            if (nextCount >= MAX_FAILED_ATTEMPTS) {
                return new AttemptRecord(existing.firstFailureAt, nextCount, now.plus(LOCK_DURATION));
            }
            return new AttemptRecord(existing.firstFailureAt, nextCount, null);
        });

        boolean nowLocked = updated.lockedUntil != null;
        if (nowLocked) {
            log.warn(
                    "Login lockout engaged for email={} ip={} after {} failed attempts; locked until {}.",
                    email,
                    clientIp,
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

    private String key(String email, String clientIp) {
        String normalisedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalisedIp = clientIp == null ? "" : clientIp.trim();
        return normalisedEmail + "|" + normalisedIp;
    }

    private record AttemptRecord(Instant firstFailureAt, int failedAttempts, Instant lockedUntil) {
    }
}
