package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
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

        HealthController health = new HealthController(
                new DatabaseHealthProbe(unreachable), new AiAvailabilityService(new AiProperties()));

        var entity = health.getHealth();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        var response = entity.getBody();
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Service is degraded");
        assertThat(response.data().status()).isEqualTo("DOWN");
        assertThat(response.data().ai()).isEqualTo("UNAVAILABLE");
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
    }
}
