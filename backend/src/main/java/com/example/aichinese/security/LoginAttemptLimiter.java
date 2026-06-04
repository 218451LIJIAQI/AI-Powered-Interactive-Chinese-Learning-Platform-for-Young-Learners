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

import com.example.aichinese.util.TextUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    static final int MAX_FAILED_ATTEMPTS = 5;

    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

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

        if (!record.firstFailureAt.plus(attemptWindow).isAfter(now)) {
            attempts.asMap().remove(key, record);
        }
        return true;
    }

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
        String normalisedEmail = TextUtils.safeTruncate(
                email == null ? "" : email.strip().toLowerCase(Locale.ROOT), MAX_EMAIL_KEY_LENGTH);
        String normalisedIp = TextUtils.safeTruncate(
                clientIp == null ? "" : clientIp.strip(), MAX_IP_KEY_LENGTH);
        return normalisedEmail + "|" + normalisedIp;
    }

    private String anonymizedKey(String email, String clientIp) {
        return HexFormat.of().toHexDigits(key(email, clientIp).hashCode());
    }

    private record AttemptRecord(Instant firstFailureAt, int failedAttempts) {
    }
}
