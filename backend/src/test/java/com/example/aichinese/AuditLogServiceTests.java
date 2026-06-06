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

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.CommunicationMapper;

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

    @Test
    void recordRedactsSecretsAndBoundsDescriptionLength() {
        AuditLogRepository auditLogRepository = savingRepository();
        AuditLogService service = service(auditLogRepository);

        service.record(actor(ACTOR_ID, ACTOR_EMAIL), ACTION_TYPE, TARGET_TYPE, TARGET_ID, sensitiveLongDescription());

        String description = savedAuditLog(auditLogRepository).getDescription();
        assertThat(description)
                .contains("password=[REDACTED]")
                .contains("token=[REDACTED]")
                .contains("api_key=[REDACTED]")
                .contains("Authorization: [REDACTED]")
                .doesNotContain("SuperSecret123", "abc123", "bearer-token", "reset-token")
                .hasSizeLessThanOrEqualTo(MAX_AUDIT_DESCRIPTION_LENGTH);
    }

    @Test
    void recordUsesPrincipalSnapshotAndManagedUserReference() {
        AuditLogRepository auditLogRepository = savingRepository();
        UserRepository userRepository = mock(UserRepository.class);
        User actorReference = actor(ACTOR_ID, ACTOR_EMAIL);
        when(userRepository.getReferenceById(ACTOR_ID)).thenReturn(actorReference);
        AuditLogService service = service(auditLogRepository, userRepository);

        service.record(
                new SecurityUserDetails(actor(ACTOR_ID, ACTOR_EMAIL)),
                ACTION_TYPE,
                TARGET_TYPE,
                TARGET_ID,
                "Principal-backed audit");

        verify(userRepository).getReferenceById(ACTOR_ID);
        AuditLog saved = savedAuditLog(auditLogRepository);
        assertThat(saved.getActorUser()).isSameAs(actorReference);
        assertThat(saved.getActorEmail()).isEqualTo(ACTOR_EMAIL);
    }

    @Test
    void safeRecordLogsConciseFailureSummaryWithoutWarnStackTrace(CapturedOutput output) {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("audit store failed Authorization: Bearer secret-token"));
        AuditLogService service = service(auditLogRepository);

        assertThatCode(() -> service.safeRecord(
                null,
                ACTION_TYPE,
                TARGET_TYPE,
                TARGET_ID,
                "description",
                "127.0.0.1"))
                .doesNotThrowAnyException();

        assertThat(output)
                .contains("Audit log creation failed for action SECURITY_EVENT on USER:10: RuntimeException")
                .contains("Authorization: [REDACTED]")
                .doesNotContain("secret-token", "at com.example.aichinese");
    }

    private static AuditLogRepository savingRepository() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return auditLogRepository;
    }

    private static AuditLogService service(AuditLogRepository auditLogRepository) {
        return service(auditLogRepository, mock(UserRepository.class));
    }

    private static AuditLogService service(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        return new AuditLogService(auditLogRepository, mock(CommunicationMapper.class), userRepository);
    }

    private static AuditLog savedAuditLog(AuditLogRepository auditLogRepository) {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private static User actor(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private static String sensitiveLongDescription() {
        return "password=SuperSecret123 token:abc123 api_key=sk-live "
                + "Authorization: Bearer bearer-token "
                + "resetLink=https://example.test/reset?token=reset-token "
                + "x".repeat(MAX_AUDIT_DESCRIPTION_LENGTH + 200);
    }
}
