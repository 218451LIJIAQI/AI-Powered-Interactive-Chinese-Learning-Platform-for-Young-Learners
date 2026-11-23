package com.example.aichinese.service;

import java.sql.Connection;
import java.time.Duration;
import java.util.function.LongSupplier;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.aichinese.util.ExceptionLogUtils;

/**
 * Database liveness probe with short-TTL result coalescing and deduplicated failure logging so public health-check
 * bursts cannot exhaust HikariCP or flood logs during an outage.
 */
@Component
public class DatabaseHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthProbe.class);
    private static final int DATABASE_CHECK_TIMEOUT_SECONDS = 2;
    private static final long CACHE_TTL_NANOS = Duration.ofSeconds(3).toNanos();

    private final DataSource dataSource;
    private final LongSupplier nanoClock;

    private final Object probeLock = new Object();
    private volatile CachedProbe cachedProbe;
    private boolean hadFailure;

    @Autowired
    public DatabaseHealthProbe(DataSource dataSource) {
        this(dataSource, System::nanoTime);
    }

    DatabaseHealthProbe(DataSource dataSource, LongSupplier nanoClock) {
        this.dataSource = dataSource;
        this.nanoClock = nanoClock;
    }

    private record CachedProbe(boolean up, long expiresAtNanos) {
        boolean isFresh(long now) {
            return expiresAtNanos - now > 0;
        }
    }

    public boolean isDatabaseUp() {
        long now = nanoClock.getAsLong();
        CachedProbe snapshot = cachedProbe;
        if (snapshot != null && snapshot.isFresh(now)) {
            return snapshot.up();
        }
        synchronized (probeLock) {
            now = nanoClock.getAsLong();
            snapshot = cachedProbe;
            if (snapshot != null && snapshot.isFresh(now)) {
                return snapshot.up();
            }
            boolean up = probeDatabase();
            cachedProbe = new CachedProbe(up, nanoClock.getAsLong() + CACHE_TTL_NANOS);
            return up;
        }
    }

    private boolean probeDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(DATABASE_CHECK_TIMEOUT_SECONDS);
            if (!valid && !hadFailure) {
                log.warn("Health check database probe returned invalid connection.");
            }
            hadFailure = !valid;
            return valid;
        } catch (Exception unavailable) {
            if (!hadFailure) {
                log.warn("Health check database probe failed: {}", ExceptionLogUtils.safeSummary(unavailable));
            }
            hadFailure = true;
            return false;
        }
    }
}
