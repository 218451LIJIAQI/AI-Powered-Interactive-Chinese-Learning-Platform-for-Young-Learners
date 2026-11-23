package com.example.aichinese.security;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/** Advances only when a limiter test moves it. */
final class MutableInstantSource implements InstantSource {

    private Instant now = Instant.parse("2025-01-01T00:00:00Z");

    @Override
    public Instant instant() {
        return now;
    }

    void advance(Duration duration) {
        now = now.plus(duration);
    }
}
