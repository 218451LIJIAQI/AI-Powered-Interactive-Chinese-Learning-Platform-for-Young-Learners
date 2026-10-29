package com.example.aichinese.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UTC test clock for the limiter suites: starts at 2025-01-01T00:00:00Z and only
 * moves when a test advances it.
 */
final class MutableClock extends Clock {

    private static final Instant START_TIME = Instant.parse("2025-01-01T00:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final AtomicReference<Instant> now = new AtomicReference<>(START_TIME);

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

    void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }
}
