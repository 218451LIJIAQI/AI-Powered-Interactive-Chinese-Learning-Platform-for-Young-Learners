package com.example.aichinese.security;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import com.example.aichinese.util.TextUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Shared fixed-window counter mechanics for in-memory auth rate limiters. Wires a Caffeine {@link Ticker} to the
 * injected {@link InstantSource} so cache expiration stays aligned with logical clocks in tests and production.
 */
final class FixedWindowCounter {

    private final Cache<String, WindowRecord> windows;
    private final InstantSource clock;
    private final Duration window;

    FixedWindowCounter(InstantSource clock, Duration window, int maxTrackedRecords) {
        this.clock = clock;
        this.window = window;
        this.windows = Caffeine.newBuilder()
                .maximumSize(maxTrackedRecords)
                .expireAfterAccess(window)
                .ticker(tickerFor(clock))
                .build();
    }

    static Ticker tickerFor(InstantSource clock) {
        if (clock == InstantSource.system()) {
            return Ticker.systemTicker();
        }
        Instant origin = clock.instant();
        AtomicLong highWaterMark = new AtomicLong();
        return () -> {
            long elapsed = Duration.between(origin, clock.instant()).toNanos();
            return highWaterMark.accumulateAndGet(elapsed, Math::max);
        };
    }

    static String normalizeKeyPart(String value, int maxLength, boolean lowerCase) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        if (lowerCase) {
            stripped = stripped.toLowerCase(Locale.ROOT);
        }
        return TextUtils.safeTruncate(stripped, maxLength);
    }

    boolean isExhausted(String key, int maxRequests) {
        WindowRecord existing = windows.getIfPresent(key);
        if (existing == null) {
            return false;
        }
        return existing.windowStartedAt.plus(window).isAfter(clock.instant())
                && existing.count >= maxRequests;
    }

    long increment(String key) {
        Instant now = clock.instant();
        WindowRecord updated = windows.asMap().compute(key, (ignored, existing) ->
                existing == null || !existing.windowStartedAt.plus(window).isAfter(now)
                        ? new WindowRecord(now, 1)
                        : new WindowRecord(existing.windowStartedAt, existing.count + 1));
        return updated.count;
    }

    void invalidate(String key) {
        windows.invalidate(key);
    }

    long cleanUpAndEstimatedSize() {
        windows.cleanUp();
        return windows.estimatedSize();
    }

    private record WindowRecord(Instant windowStartedAt, long count) {
    }
}
