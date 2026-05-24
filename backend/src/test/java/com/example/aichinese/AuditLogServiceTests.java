package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.CommunicationMapper;

/**
 * Unit coverage for {@code AuditLogService} — pins the secret-redaction
 * regex (tokens / passwords / API keys / authorization headers / reset
 * links) and the {@code MAX_AUDIT_DESCRIPTION_LENGTH} cap so an admin
 * browsing the audit history cannot accidentally lift sensitive values
 * from a description field.
 */
class AuditLogServiceTests {

    @Test
    void recordRedactsSecretsAndBoundsDescriptionLength() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuditLogService service = new AuditLogService(auditLogRepository, mock(CommunicationMapper.class));

        User actor = new User();
        actor.setEmail("admin@example.com");
        String longDescription = "password=SuperSecret123 token:abc123 api_key=sk-live Authorization: Bearer bearer-token resetLink=https://example.test/reset?token=reset-token " + "x".repeat(1200);

        service.record(actor, "SECURITY_EVENT", "USER", 10L, longDescription);

        org.mockito.ArgumentCaptor<AuditLog> captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        org.mockito.Mockito.verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getDescription()).contains("password=[REDACTED]");
        assertThat(saved.getDescription()).contains("token=[REDACTED]");
        assertThat(saved.getDescription()).contains("api_key=[REDACTED]");
        assertThat(saved.getDescription()).doesNotContain("SuperSecret123");
        assertThat(saved.getDescription()).doesNotContain("abc123");
        assertThat(saved.getDescription()).contains("Authorization: [REDACTED]");
        assertThat(saved.getDescription()).doesNotContain("bearer-token");
        assertThat(saved.getDescription()).doesNotContain("reset-token");
        assertThat(saved.getDescription()).hasSizeLessThanOrEqualTo(1000);
    }
}
