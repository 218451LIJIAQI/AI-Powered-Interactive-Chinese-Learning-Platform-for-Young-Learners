package com.example.aichinese.security;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.aichinese.service.SecureTokens;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Brute-force guard of sign-in and of the current-password check (SDD 3.4): five failed attempts for one e-mail
 * address from one client IP within 15 minutes lock that pair for 15 minutes. The address is compared ignoring case.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    static final int MAX_FAILED_ATTEMPTS = 5;

    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    static final int MAX_TRACKED_ATTEMPT_RECORDS = 10_000;
    private static final int MAX_EMAIL_KEY_LENGTH = 320;
    private static final int MAX_IP_KEY_LENGTH = 64;
    private static final int ANONYMIZED_KEY_LENGTH = 16;
    private static final int LOCK_STRIPES = 64;

    private final FixedWindowCounter attempts;
    private final Cache<String, Instant> lockouts;
    private final InstantSource clock;
    private final Object[] keyStripes;

    @Autowired
    public LoginAttemptLimiter() {
        this(InstantSource.system(), MAX_TRACKED_ATTEMPT_RECORDS);
    }

    LoginAttemptLimiter(InstantSource clock, int maxTrackedAttemptRecords) {
        this.clock = clock;
        this.attempts = new FixedWindowCounter(clock, ATTEMPT_WINDOW, maxTrackedAttemptRecords);
        this.lockouts = Caffeine.newBuilder()
                .maximumSize(maxTrackedAttemptRecords)
                .expireAfterWrite(LOCK_DURATION)
                .ticker(FixedWindowCounter.tickerFor(clock))
                .build();
        this.keyStripes = new Object[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            this.keyStripes[i] = new Object();
        }
    }

    /** How long the pair stays locked; zero when it is not locked. */
    public Duration retryAfter(String email, String clientIp) {
        String key = key(email, clientIp);
        Instant lockedUntil = lockouts.getIfPresent(key);
        if (lockedUntil == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(clock.instant(), lockedUntil);
        if (remaining.isZero() || remaining.isNegative()) {
            lockouts.asMap().remove(key, lockedUntil);
            return Duration.ZERO;
        }
        return remaining;
    }

    /** Counts a failed attempt; returns {@code true} only when this failure engages a new lockout. */
    public boolean recordFailure(String email, String clientIp) {
        String key = key(email, clientIp);
        synchronized (stripeFor(key)) {
            Instant now = clock.instant();
            Instant existingLockout = lockouts.getIfPresent(key);
            if (existingLockout != null && existingLockout.isAfter(now)) {
                return false;
            }

            long failedAttempts = attempts.increment(key);
            boolean nowLocked = failedAttempts >= MAX_FAILED_ATTEMPTS;
            if (nowLocked) {
                Instant lockedUntil = now.plus(LOCK_DURATION);
                lockouts.put(key, lockedUntil);
                attempts.invalidate(key);
                log.warn(
                        "Login lockout engaged for key={} after {} failed attempts; locked until {}.",
                        anonymized(key),
                        failedAttempts,
                        lockedUntil);
            }
            return nowLocked;
        }
    }

    public void recordSuccess(String email, String clientIp) {
        String key = key(email, clientIp);
        synchronized (stripeFor(key)) {
            attempts.invalidate(key);
            lockouts.invalidate(key);
        }
    }

    int trackedAttemptCount() {
        lockouts.cleanUp();
        return Math.toIntExact(attempts.cleanUpAndEstimatedSize() + lockouts.estimatedSize());
    }

    private Object stripeFor(String key) {
        return keyStripes[Math.floorMod(key.hashCode(), LOCK_STRIPES)];
    }

    private static String key(String email, String clientIp) {
        String normalisedEmail = FixedWindowCounter.normalizeKeyPart(email, MAX_EMAIL_KEY_LENGTH, true);
        String normalisedIp = FixedWindowCounter.normalizeKeyPart(clientIp, MAX_IP_KEY_LENGTH, false);
        return normalisedEmail + "|" + normalisedIp;
    }

    /** The key for a log line: a prefix of its hash, so the line names neither the e-mail address nor the IP. */
    private static String anonymized(String key) {
        return SecureTokens.sha256Hex(key).substring(0, ANONYMIZED_KEY_LENGTH);
    }
}
