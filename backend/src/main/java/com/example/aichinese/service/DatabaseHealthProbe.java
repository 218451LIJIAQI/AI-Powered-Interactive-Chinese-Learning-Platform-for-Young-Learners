package com.example.aichinese.service;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.aichinese.util.ExceptionLogUtils;

import jakarta.annotation.PreDestroy;

/**
 * Database liveness probe with short-TTL result coalescing, bounded connection acquisition time, and deduplicated
 * failure logging so public health-check bursts cannot exhaust HikariCP, stall servlet threads, or flood logs during
 * an outage.
 */
@Component
public class DatabaseHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthProbe.class);
    private static final int DATABASE_CHECK_TIMEOUT_SECONDS = 2;
    private static final long PROBE_WAIT_TIMEOUT_MILLIS = 3_000L;
    private static final long CACHE_TTL_NANOS = Duration.ofSeconds(3).toNanos();

    private final DataSource dataSource;
    private final LongSupplier nanoClock;
    private final long probeWaitTimeoutMillis;
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "db-health-probe");
        thread.setDaemon(true);
        return thread;
    });

    private final Object probeLock = new Object();
    private volatile CachedProbe cachedProbe;
    /** Tracks whether a probe task is queued or still executing JDBC I/O even after cancellation. */
    private final AtomicBoolean probeRunning = new AtomicBoolean(false);
    private boolean hadFailure;

    private record ProbeOutcome(boolean up, String failureWarning) {
    }

    /** Plain Runnable so the single-threaded executor always runs its outer finally and resets {@link #probeRunning}. */
    private final class ProbeTask implements Runnable {
        final CompletableFuture<ProbeOutcome> outcome = new CompletableFuture<>();
        private final Object cancelLock = new Object();
        private boolean cancelled;
        private Thread worker;

        @Override
        public void run() {
            ProbeOutcome result = null;
            Throwable failure = null;
            boolean ran = false;
            try {
                synchronized (cancelLock) {
                    if (cancelled) {
                        return;
                    }
                    worker = Thread.currentThread();
                }
                ran = true;
                try {
                    result = probeDatabase();
                } catch (Throwable thrown) {
                    failure = thrown;
                } finally {
                    synchronized (cancelLock) {
                        worker = null;
                    }
                    // Clear any late interrupt before releasing probeRunning so the next probe starts clean.
                    Thread.interrupted();
                }
            } finally {
                probeRunning.set(false);
                if (ran) {
                    if (failure != null) {
                        outcome.completeExceptionally(failure);
                    } else {
                        outcome.complete(result);
                    }
                }
            }
        }

        void cancel() {
            synchronized (cancelLock) {
                cancelled = true;
                if (worker != null) {
                    worker.interrupt();
                }
            }
        }
    }

    @Autowired
    public DatabaseHealthProbe(DataSource dataSource) {
        this(dataSource, System::nanoTime, PROBE_WAIT_TIMEOUT_MILLIS);
    }

    DatabaseHealthProbe(DataSource dataSource, LongSupplier nanoClock) {
        this(dataSource, nanoClock, PROBE_WAIT_TIMEOUT_MILLIS);
    }

    DatabaseHealthProbe(DataSource dataSource, LongSupplier nanoClock, long probeWaitTimeoutMillis) {
        this.dataSource = dataSource;
        this.nanoClock = nanoClock;
        this.probeWaitTimeoutMillis = probeWaitTimeoutMillis;
    }

    @PreDestroy
    void shutdown() {
        probeExecutor.shutdownNow();
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
            boolean up = probeBounded();
            cachedProbe = new CachedProbe(up, nanoClock.getAsLong() + CACHE_TTL_NANOS);
            return up;
        }
    }

    private boolean probeBounded() {
        if (!probeRunning.compareAndSet(false, true)) {
            if (!hadFailure) {
                log.warn("Health check database probe is still stalled waiting for a database connection.");
            }
            hadFailure = true;
            return false;
        }
        ProbeTask task = new ProbeTask();
        boolean submitted = false;
        try {
            probeExecutor.execute(task);
            submitted = true;
        } catch (RuntimeException rejected) {
            if (!hadFailure) {
                log.warn("Health check database probe failed: {}", ExceptionLogUtils.safeSummary(rejected));
            }
            hadFailure = true;
            return false;
        } finally {
            if (!submitted) {
                probeRunning.set(false);
            }
        }
        try {
            ProbeOutcome outcome = task.outcome.get(probeWaitTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!outcome.up() && !hadFailure && outcome.failureWarning() != null) {
                log.warn(outcome.failureWarning());
            }
            hadFailure = !outcome.up();
            return outcome.up();
        } catch (TimeoutException timedOut) {
            task.cancel();
            if (!hadFailure) {
                log.warn("Health check database probe timed out after {} ms.", probeWaitTimeoutMillis);
            }
            hadFailure = true;
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            task.cancel();
            hadFailure = true;
            return false;
        } catch (Exception unavailable) {
            if (!hadFailure) {
                log.warn("Health check database probe failed: {}", ExceptionLogUtils.safeSummary(unavailable));
            }
            hadFailure = true;
            return false;
        }
    }

    private ProbeOutcome probeDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(DATABASE_CHECK_TIMEOUT_SECONDS);
            return valid
                    ? new ProbeOutcome(true, null)
                    : new ProbeOutcome(false, "Health check database probe returned invalid connection.");
        } catch (Exception unavailable) {
            return new ProbeOutcome(
                    false, "Health check database probe failed: " + ExceptionLogUtils.safeSummary(unavailable));
        }
    }
}
