package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.controller.HealthController;
import com.example.aichinese.service.ai.AiAvailabilityService;

class HealthControllerTests {

    @Test
    void reportsDownAndDegradedWhenDatabaseRefusesConnection() throws Exception {
        DataSource unreachable = mock(DataSource.class);
        when(unreachable.getConnection()).thenThrow(new SQLException("Connection refused"));

        DatabaseHealthProbe probe = new DatabaseHealthProbe(unreachable);
        HealthController health = new HealthController(probe, new AiAvailabilityService(new AiProperties()));
        try {
            var entity = health.getHealth();
            assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(entity.getHeaders().getCacheControl()).contains("no-store");
            var response = entity.getBody();
            assertThat(response).isNotNull();
            assertThat(response.success()).isFalse();
            assertThat(response.message()).isEqualTo("Service is degraded");
            assertThat(response.data().status()).isEqualTo("DOWN");
            assertThat(response.data().ai()).isEqualTo("UNAVAILABLE");
        } finally {
            probe.shutdown();
        }
    }

    @Test
    void reportsDownWhenConnectionIsInvalidAndCoalescesProbesWithinTtl() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection invalid = mock(Connection.class);
        Connection valid = mock(Connection.class);
        when(invalid.isValid(2)).thenReturn(false);
        when(valid.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(invalid, valid);

        AtomicLong now = new AtomicLong(1_000L);
        DatabaseHealthProbe probe = new DatabaseHealthProbe(dataSource, now::get);
        HealthController health = new HealthController(probe, new AiAvailabilityService(new AiProperties()));
        try {
            var firstDown = health.getHealth();
            assertThat(firstDown.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(firstDown.getBody().data().status()).isEqualTo("DOWN");
            // Within 3-second TTL, cached result is reused without touching the pool again.
            now.addAndGet(Duration.ofSeconds(1).toNanos());
            var cachedDown = health.getHealth();
            assertThat(cachedDown.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(cachedDown.getBody().data().status()).isEqualTo("DOWN");
            verify(dataSource, times(1)).getConnection();

            // After TTL expires, probe re-checks and observes recovery.
            now.addAndGet(Duration.ofSeconds(3).toNanos());
            var recoveredEntity = health.getHealth();
            assertThat(recoveredEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            var recovered = recoveredEntity.getBody();
            assertThat(recovered).isNotNull();
            assertThat(recovered.success()).isTrue();
            assertThat(recovered.message()).isEqualTo("Service is running");
            assertThat(recovered.data().status()).isEqualTo("UP");
            verify(dataSource, times(2)).getConnection();
        } finally {
            probe.shutdown();
        }
    }

    @Test
    void shortCircuitsSubsequentProbesWhilePreviousWorkerIsStalledIgnoringInterrupts() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean stall = new AtomicBoolean(false);
        Connection valid = mock(Connection.class);
        when(valid.isValid(2)).thenReturn(true);
        DataSource stalled = mock(DataSource.class);
        when(stalled.getConnection()).thenAnswer(invocation -> {
            if (!stall.get()) {
                return valid;
            }
            entered.countDown();
            while (true) {
                try {
                    if (release.await(10, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                } catch (InterruptedException ignored) {
                    // Simulate an uninterruptible JDBC driver blocking inside socket connect.
                }
            }
            throw new SQLException("Released after test");
        });

        AtomicLong now = new AtomicLong(1_000L);
        DatabaseHealthProbe probe = new DatabaseHealthProbe(stalled, now::get, 400L);
        try {
            // Warm up the background worker thread and Mockito stub before engaging the stall.
            assertThat(probe.isDatabaseUp()).isTrue();
            now.addAndGet(Duration.ofSeconds(4).toNanos());
            stall.set(true);

            assertThat(probe.isDatabaseUp()).isFalse();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            // After TTL expires, the stalled worker is still running: must return false without queueing a new connect.
            now.addAndGet(Duration.ofSeconds(4).toNanos());
            assertThat(probe.isDatabaseUp()).isFalse();
            verify(stalled, times(2)).getConnection();

            // Once the stalled worker finishes, subsequent probes recover cleanly.
            stall.set(false);
            release.countDown();
            assertThat(awaitDatabaseUp(probe, now)).isTrue();
        } finally {
            release.countDown();
            probe.shutdown();
        }
    }

    @Test
    void recoversAfterCallerInterruptCancelsProbeBeforeWorkerStarts() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection valid = mock(Connection.class);
        when(valid.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(valid);

        AtomicLong now = new AtomicLong(1_000L);
        DatabaseHealthProbe probe = new DatabaseHealthProbe(dataSource, now::get, 500L);
        try {
            Thread.currentThread().interrupt();
            assertThat(probe.isDatabaseUp()).isFalse();
            assertThat(Thread.interrupted()).isTrue();

            // Even if cancellation happened before the worker started, probeRunning must reset and allow recovery.
            assertThat(awaitDatabaseUp(probe, now)).isTrue();
        } finally {
            Thread.interrupted();
            probe.shutdown();
        }
    }

    private static boolean awaitDatabaseUp(DatabaseHealthProbe probe, AtomicLong now) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            now.addAndGet(Duration.ofSeconds(4).toNanos());
            if (probe.isDatabaseUp()) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }
}
