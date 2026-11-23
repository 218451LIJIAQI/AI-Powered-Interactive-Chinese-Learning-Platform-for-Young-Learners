package com.example.aichinese.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.PlatformProperties;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.learn.LearnClock;

import jakarta.persistence.EntityManager;

/**
 * Verifies that exported CSV timestamps align with platform-day filters when the platform timezone differs from the
 * JVM's default timezone.
 */
class AuditLogQueryServiceTests {

    @Test
    void csvExportWritesTimestampsInPlatformZoneWithExplicitOffset() {
        Instant instant = Instant.parse("2026-06-15T16:30:00Z");
        ZoneOffset serverOffset = ZoneId.systemDefault().getRules().getOffset(instant);
        // Choose a platform zone whose UTC offset guarantees a different calendar date from ZoneId.systemDefault().
        String platformZone = serverOffset.getTotalSeconds() >= 8 * 3600
                ? "Pacific/Pago_Pago"
                : "Pacific/Kiritimati";
        ZoneId targetZone = ZoneId.of(platformZone);
        LearnClock clock = new LearnClock(new PlatformProperties(platformZone));

        LocalDateTime serverLocal = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        LocalDate expectedDate = LocalDate.ofInstant(instant, targetZone);
        assertThat(expectedDate).isNotEqualTo(serverLocal.toLocalDate());
        String expectedOffset = targetZone.getRules().getOffset(instant).getId();

        AuditLogQueryService service = new AuditLogQueryService(
                mock(AuditLogRepository.class), mock(AuditLogService.class), clock, mock(EntityManager.class));
        var row = new AuditLogQueryService.ExportRow(
                serverLocal, "LOGIN", "Admin", "admin@example.com", "User", 1L, "Admin", null, "Signed in.");

        String csv = service.renderCsv(List.of(row), Map.of());

        assertThat(csv).startsWith("Time,Action,Category,Actor,Actor email,Target,Reason,Description\r\n");
        assertThat(csv).contains(expectedDate + " ");
        assertThat(csv).contains(expectedOffset + ",LOGIN,");
    }
}
