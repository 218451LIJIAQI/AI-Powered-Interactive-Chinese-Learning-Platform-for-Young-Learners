package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiUsageLimiter;

/**
 * Unit coverage for {@code PlatformSettingsService}: the defaults applied when setting rows are missing (AI notice,
 * sensitive-word filter, AI safety check), the limits read from the server configuration, and "last changed".
 */
class PlatformSettingsServiceTests {

    private static final String DEFAULT_AI_NOTICE =
            "AI can make mistakes.";
    private static final String UPDATED_AI_NOTICE = "Check important answers with your lesson.";
    private static final int UPDATED_SETTINGS_COUNT = 3;

    private final SystemSettingRepository repository = mock(SystemSettingRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final CurrentAccount currentAccount = mock(CurrentAccount.class);
    private final AiUsageLimiter limiter = mock(AiUsageLimiter.class);

    @Test
    void aiSettingsFallBackToDefaultsAndShowTheConfiguredLimits() {
        when(limiter.requestsPerMinute()).thenReturn(10);
        when(limiter.requestsPerDay()).thenReturn(100);

        AdminAiSettingsResponse response = service(new AiProperties()).getAiSettings();

        assertThat(response.aiNoticeText()).isEqualTo(DEFAULT_AI_NOTICE);
        assertThat(response.sensitiveWordFilter()).isTrue();
        assertThat(response.aiSafetyCheck()).isTrue();
        assertThat(response.requestsPerMinute()).isEqualTo(10);
        assertThat(response.requestsPerDay()).isEqualTo(100);
        assertThat(response.lastChangedAt()).isNull();
        assertThat(response.lastChangedBy()).isNull();
    }

    @Test
    void updateCreatesMissingRowsAndReportsTheLatestChange() {
        User admin = new User();
        admin.setDisplayName("Olivia Tan");
        SecurityUserDetails principal = new SecurityUserDetails(admin);
        when(currentAccount.lock(principal)).thenReturn(admin);
        AuditLog change = new AuditLog();
        change.setActorUser(admin);
        change.setActionType(PlatformSettingsService.SETTINGS_UPDATED_ACTION);
        when(repository.findLockedBySettingKeyInOrderBySettingKeyAsc(anyList())).thenReturn(List.of());
        when(auditLogService.record(eq(admin), eq(PlatformSettingsService.SETTINGS_UPDATED_ACTION),
                eq("SYSTEM_SETTING"), isNull(), anyString())).thenReturn(change);

        AdminAiSettingsResponse response = service(new AiProperties()).updateAiSettings(
                principal, new AdminAiSettingsUpdateRequest(UPDATED_AI_NOTICE, true, false));

        verify(currentAccount).lock(principal);
        ArgumentCaptor<SystemSetting> saved = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository, times(UPDATED_SETTINGS_COUNT)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(SystemSetting::getSettingKey, SystemSetting::getSettingValue)
                .containsExactlyInAnyOrder(
                        tuple("ai_disclaimer_text", UPDATED_AI_NOTICE),
                        tuple("sensitive_word_filter_enabled", "true"),
                        tuple("ai_safety_check_enabled", "false"));
        verify(repository).findLockedBySettingKeyInOrderBySettingKeyAsc(
                List.of("ai_disclaimer_text", "sensitive_word_filter_enabled", "ai_safety_check_enabled"));
        verifyNoInteractions(auditLogRepository);
        assertThat(response.aiNoticeText()).isEqualTo(UPDATED_AI_NOTICE);
        assertThat(response.sensitiveWordFilter()).isTrue();
        assertThat(response.aiSafetyCheck()).isFalse();
        assertThat(response.lastChangedBy()).isEqualTo("Olivia Tan");
    }

    @Test
    void updateMutatesExistingRowsAndGetAiSettingsReadsStoredFalseFlags() {
        SystemSetting disclaimer = new SystemSetting();
        disclaimer.setSettingKey("ai_disclaimer_text");
        disclaimer.setSettingValue(DEFAULT_AI_NOTICE);
        SystemSetting wordFilter = new SystemSetting();
        wordFilter.setSettingKey("sensitive_word_filter_enabled");
        wordFilter.setSettingValue("true");
        SystemSetting safetyCheck = new SystemSetting();
        safetyCheck.setSettingKey("ai_safety_check_enabled");
        safetyCheck.setSettingValue("true");

        User admin = new User();
        admin.setDisplayName("Olivia Tan");
        SecurityUserDetails principal = new SecurityUserDetails(admin);
        when(currentAccount.lock(principal)).thenReturn(admin);
        when(repository.findLockedBySettingKeyInOrderBySettingKeyAsc(anyList()))
                .thenReturn(List.of(disclaimer, wordFilter, safetyCheck));
        AuditLog change = new AuditLog();
        change.setActorUser(admin);
        when(auditLogService.record(eq(admin), eq(PlatformSettingsService.SETTINGS_UPDATED_ACTION),
                eq("SYSTEM_SETTING"), isNull(), anyString())).thenReturn(change);

        service(new AiProperties()).updateAiSettings(
                principal, new AdminAiSettingsUpdateRequest(UPDATED_AI_NOTICE, false, false));

        assertThat(disclaimer.getSettingValue()).isEqualTo(UPDATED_AI_NOTICE);
        assertThat(wordFilter.getSettingValue()).isEqualTo("false");
        assertThat(safetyCheck.getSettingValue()).isEqualTo("false");

        when(repository.findBySettingKeyIn(anyList())).thenReturn(List.of(disclaimer, wordFilter, safetyCheck));
        AdminAiSettingsResponse loaded = service(new AiProperties()).getAiSettings();
        assertThat(loaded.aiNoticeText()).isEqualTo(UPDATED_AI_NOTICE);
        assertThat(loaded.sensitiveWordFilter()).isFalse();
        assertThat(loaded.aiSafetyCheck()).isFalse();
    }

    @Test
    void settingDefinitionsListTheThreeSettingsWithTheirDefaults() {
        assertThat(PlatformSettingsService.SETTING_DEFINITIONS)
                .extracting(PlatformSettingsService.SettingDefinition::key)
                .containsExactly("ai_disclaimer_text", "sensitive_word_filter_enabled", "ai_safety_check_enabled");
        SystemSetting setting = PlatformSettingsService.SETTING_DEFINITIONS.get(0).newSetting();
        assertThat(setting.getSettingValue()).isEqualTo(DEFAULT_AI_NOTICE);
    }

    /** FRPUB02: the responsible-AI page names the configured model; a blank model name is reported as none. */
    @Test
    void publicSettingsNameTheConfiguredAiModel() {
        AiProperties configured = new AiProperties();
        configured.setModelName("  example-model-1  ");
        assertThat(service(configured).getPublicPlatformSettings().aiModelName())
                .isEqualTo("example-model-1");

        AiProperties unset = new AiProperties();
        unset.setModelName("   ");
        assertThat(service(unset).getPublicPlatformSettings().aiModelName()).isNull();
    }

    /** The privacy and responsible-AI pages describe the provider requests really go to, and none while AI is off. */
    @Test
    void publicSettingsNameTheProviderOnlyWhileRequestsCanReachIt() {
        AiProperties deepseek = new AiProperties();
        deepseek.setProvider(" DeepSeek ");
        deepseek.setApiKey("test-key");
        deepseek.setEnableRealProvider(true);
        assertThat(service(deepseek).getPublicPlatformSettings().aiProvider())
                .isEqualTo("deepseek");

        AiProperties switchedOff = new AiProperties();
        switchedOff.setApiKey("test-key");
        assertThat(service(switchedOff).getPublicPlatformSettings().aiProvider())
                .isNull();

        AiProperties withoutKey = new AiProperties();
        withoutKey.setEnableRealProvider(true);
        assertThat(service(withoutKey).getPublicPlatformSettings().aiProvider())
                .isNull();
    }

    private PlatformSettingsService service(AiProperties aiProperties) {
        return new PlatformSettingsService(repository, auditLogRepository, auditLogService, currentAccount, limiter,
                new AiAvailabilityService(aiProperties));
    }
}
