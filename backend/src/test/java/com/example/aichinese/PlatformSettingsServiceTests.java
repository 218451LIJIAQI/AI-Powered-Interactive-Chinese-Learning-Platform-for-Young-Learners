package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AdminAiSettingsResponse;
import com.example.aichinese.dto.ai.AdminAiSettingsUpdateRequest;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.PlatformSettingsService;
import com.example.aichinese.service.ai.AiUsageLimiter;

/**
 * Unit coverage for {@code PlatformSettingsService}: the defaults applied when setting rows are missing (AI notice,
 * sensitive-word filter, AI safety check), the limits read from the server configuration, and "last changed".
 */
class PlatformSettingsServiceTests {

    private static final String DEFAULT_AI_NOTICE =
            "AI responses may contain errors and do not replace your teacher.";
    private static final String UPDATED_AI_NOTICE = "Teacher guidance is still important.";
    private static final int UPDATED_SETTINGS_COUNT = 3;

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AiUsageLimiter limiter = mock(AiUsageLimiter.class);

    @Test
    @SuppressWarnings("unchecked")
    void aiSettingsFallBackToDefaultsAndShowTheConfiguredLimits() {
        when(limiter.requestsPerMinute()).thenReturn(10);
        when(limiter.requestsPerDay()).thenReturn(100);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        AdminAiSettingsResponse response = service(repositoryWithMissingSettings(), new AiProperties()).getAiSettings();

        assertThat(response.aiNoticeText()).isEqualTo(DEFAULT_AI_NOTICE);
        assertThat(response.sensitiveWordFilter()).isTrue();
        assertThat(response.aiSafetyCheck()).isTrue();
        assertThat(response.requestsPerMinute()).isEqualTo(10);
        assertThat(response.requestsPerDay()).isEqualTo(100);
        assertThat(response.lastChangedAt()).isNull();
        assertThat(response.lastChangedBy()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCreatesMissingRowsAndReportsTheLatestChange() {
        SystemSettingRepository repository = repositoryWithMissingSettings();
        when(repository.save(any(SystemSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User admin = new User();
        admin.setDisplayName("Olivia Tan");
        AuditLog change = new AuditLog();
        change.setActorUser(admin);
        change.setActionType(PlatformSettingsService.SETTINGS_UPDATED_ACTION);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(change)));

        AdminAiSettingsResponse response = service(repository, new AiProperties()).updateAiSettings(
                currentUser(), new AdminAiSettingsUpdateRequest(UPDATED_AI_NOTICE, true, false));

        verify(repository, times(UPDATED_SETTINGS_COUNT)).save(any(SystemSetting.class));
        assertThat(response.lastChangedBy()).isEqualTo("Olivia Tan");
    }

    @Test
    void settingDefinitionsNoLongerIncludeTheReadOnlyPlatformName() {
        assertThat(PlatformSettingsService.SETTING_DEFINITIONS)
                .extracting(PlatformSettingsService.SettingDefinition::key)
                .containsExactly("ai_disclaimer_text", "sensitive_word_filter_enabled", "ai_safety_check_enabled");
        SystemSetting setting = PlatformSettingsService.SETTING_DEFINITIONS.get(0).newSetting();
        assertThat(setting.getSettingValue()).isEqualTo(DEFAULT_AI_NOTICE);
        assertThat(setting.isEditable()).isTrue();
    }

    /** FRPUB02: the responsible-AI page names the configured model; a blank model name is reported as none. */
    @Test
    void publicSettingsNameTheConfiguredAiModel() {
        AiProperties configured = new AiProperties();
        configured.setModelName("  example-model-1  ");
        assertThat(service(repositoryWithMissingSettings(), configured).getPublicPlatformSettings().aiModelName())
                .isEqualTo("example-model-1");

        AiProperties unset = new AiProperties();
        unset.setModelName("   ");
        assertThat(service(repositoryWithMissingSettings(), unset).getPublicPlatformSettings().aiModelName()).isNull();
    }

    private SystemSettingRepository repositoryWithMissingSettings() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(anyString())).thenReturn(Optional.empty());
        return repository;
    }

    private PlatformSettingsService service(SystemSettingRepository repository, AiProperties aiProperties) {
        return new PlatformSettingsService(repository, auditLogRepository, mock(AuditLogService.class), aiProperties,
                limiter);
    }

    private SecurityUserDetails currentUser() {
        User user = new User();
        user.setEmail("admin@example.com");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return new SecurityUserDetails(user);
    }
}
