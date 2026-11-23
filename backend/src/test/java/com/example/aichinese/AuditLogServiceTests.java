package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;

/**
 * Unit coverage for {@code AuditLogService}: pins the secret-redaction
 * regex (tokens / passwords / API keys / authorization headers / reset
 * links) and the {@code MAX_AUDIT_DESCRIPTION_LENGTH} cap so an admin
 * browsing the audit history cannot accidentally lift sensitive values
 * from a description field.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditLogServiceTests {

    private static final long ACTOR_ID = 42L;
    private static final long TARGET_ID = 10L;
    private static final int MAX_AUDIT_DESCRIPTION_LENGTH = 1000;
    private static final String ACTOR_EMAIL = "admin@example.com";
    private static final String ACTION_TYPE = "SECURITY_EVENT";
    private static final String TARGET_TYPE = "USER";

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogService service = new AuditLogService(
            auditLogRepository, userRepository, mock(PlatformTransactionManager.class));

    @Test
    void recordRedactsSecretsAndBoundsDescriptionLength() {
        service.record(actor(), ACTION_TYPE, TARGET_TYPE, TARGET_ID, sensitiveLongDescription());

        String description = savedAuditLog().getDescription();
        assertThat(description)
                .contains("password=[REDACTED]")
                .contains("token=[REDACTED]")
                .contains("api_key=[REDACTED]")
                .contains("Authorization: [REDACTED]")
                .doesNotContain("SuperSecret123", "abc123", "sk-live", "bearer-token", "reset-token")
                .hasSizeLessThanOrEqualTo(MAX_AUDIT_DESCRIPTION_LENGTH);
    }

    @Test
    void recordUsesPrincipalSnapshotAndManagedUserReference() {
        User actorReference = actor();
        when(userRepository.getReferenceById(ACTOR_ID)).thenReturn(actorReference);

        service.record(
                new SecurityUserDetails(actor()),
                ACTION_TYPE,
                TARGET_TYPE,
                TARGET_ID,
                "Principal-backed audit");

        verify(userRepository).getReferenceById(ACTOR_ID);
        AuditLog saved = savedAuditLog();
        assertThat(saved.getActorUser()).isSameAs(actorReference);
        assertThat(saved.getActorEmail()).isEqualTo(ACTOR_EMAIL);
    }

    @Test
    void safeRecordLogsConciseFailureSummaryWithoutWarnStackTrace(CapturedOutput output) {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("audit store failed Authorization: Bearer secret-token"));

        assertThatCode(() -> service.safeRecord(
                null,
                ACTION_TYPE,
                TARGET_TYPE,
                TARGET_ID,
                "description"))
                .doesNotThrowAnyException();

        assertThat(output)
                .contains("Audit log creation failed for action SECURITY_EVENT on USER:10: RuntimeException")
                .contains("Authorization: [REDACTED]")
                .doesNotContain("secret-token", "at com.example.aichinese");
    }

    private AuditLog savedAuditLog() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private static User actor() {
        User user = new User();
        user.setId(ACTOR_ID);
        user.setEmail(ACTOR_EMAIL);
        return user;
    }

    private static String sensitiveLongDescription() {
        return "password=SuperSecret123 token:abc123 api_key=sk-live "
                + "Authorization: Bearer bearer-token "
                + "resetLink=https://example.test/reset?token=reset-token "
                + "x".repeat(MAX_AUDIT_DESCRIPTION_LENGTH + 200);
    }
}
