package com.example.aichinese.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.ai.AdminAiSettingsResponse;
import com.example.aichinese.dto.ai.AdminAiSettingsUpdateRequest;
import com.example.aichinese.dto.system.PublicPlatformSettingsResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiUsageLimiter;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * Platform settings that take effect at runtime, edited on the admin AI Settings page:
 * <ul>
 *   <li>{@code ai_disclaimer_text} &ndash; the AI notice shown with every AI answer;</li>
 *   <li>{@code sensitive_word_filter_enabled} &ndash; enforced by {@link ContentSafetyService} for class content and
 *       for student AI requests;</li>
 *   <li>{@code ai_safety_check_enabled} &ndash; enforced by {@code AiSafetyGuard} for student AI requests and replies.</li>
 * </ul>
 * The AI request limits are shown with them but come from the server configuration.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    public static final String SENSITIVE_WORD_FILTER_KEY = "sensitive_word_filter_enabled";
    public static final String AI_SAFETY_CHECK_KEY = "ai_safety_check_enabled";
    /** The audit action of a settings change; the latest one is the settings' "last changed". */
    public static final String SETTINGS_UPDATED_ACTION = "ADMIN_PLATFORM_SETTINGS_UPDATED";

    public static final String DEFAULT_AI_DISCLAIMER_TEXT =
            "AI can make mistakes.";

    private static final SettingDefinition AI_DISCLAIMER_TEXT =
            new SettingDefinition("ai_disclaimer_text", DEFAULT_AI_DISCLAIMER_TEXT);
    private static final SettingDefinition SENSITIVE_WORD_FILTER = new SettingDefinition(SENSITIVE_WORD_FILTER_KEY, "true");
    private static final SettingDefinition AI_SAFETY_CHECK = new SettingDefinition(AI_SAFETY_CHECK_KEY, "true");

    /** Every platform setting with its default value, in seeding order. */
    public static final List<SettingDefinition> SETTING_DEFINITIONS =
            List.of(AI_DISCLAIMER_TEXT, SENSITIVE_WORD_FILTER, AI_SAFETY_CHECK);
    private static final List<String> AI_SETTING_KEYS =
            SETTING_DEFINITIONS.stream().map(SettingDefinition::key).toList();

    private final SystemSettingRepository systemSettingRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final CurrentAccount currentAccount;
    private final AiUsageLimiter aiUsageLimiter;
    private final AiAvailabilityService aiAvailabilityService;

    @Transactional(readOnly = true)
    public AdminAiSettingsResponse getAiSettings() {
        Map<String, String> stored = systemSettingRepository.findBySettingKeyIn(AI_SETTING_KEYS).stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, SystemSetting::getSettingValue));
        AuditLog lastChange = auditLogRepository
                .findFirstByActionTypeOrderByCreatedAtDescIdDesc(SETTINGS_UPDATED_ACTION)
                .orElse(null);
        return aiSettingsResponse(
                AI_DISCLAIMER_TEXT.valueOrDefault(stored.get(AI_DISCLAIMER_TEXT.key())),
                !"false".equalsIgnoreCase(SENSITIVE_WORD_FILTER.valueOrDefault(stored.get(SENSITIVE_WORD_FILTER.key()))),
                !"false".equalsIgnoreCase(AI_SAFETY_CHECK.valueOrDefault(stored.get(AI_SAFETY_CHECK.key()))),
                lastChange);
    }

    private AdminAiSettingsResponse aiSettingsResponse(
            String notice, boolean wordFilter, boolean safetyCheck, AuditLog lastChange) {
        return new AdminAiSettingsResponse(
                notice,
                wordFilter,
                safetyCheck,
                aiUsageLimiter.requestsPerMinute(),
                aiUsageLimiter.requestsPerDay(),
                lastChange == null ? null : lastChange.getCreatedAt(),
                lastChange == null ? null : AuditLogService.actorName(lastChange));
    }

    @Transactional(readOnly = true)
    public String aiDisclaimerText() {
        return stringSetting(AI_DISCLAIMER_TEXT);
    }

    @Transactional(readOnly = true)
    public PublicPlatformSettingsResponse getPublicPlatformSettings() {
        AiAvailabilityService.Availability ai = aiAvailabilityService.getAvailability();
        return new PublicPlatformSettingsResponse(
                stringSetting(AI_DISCLAIMER_TEXT),
                ai.model(),
                ai.available() ? ai.provider() : null);
    }

    /** Admin "Sensitive-word filter" toggle: {@link ContentSafetyService} screens class content and AI requests. */
    @Transactional(readOnly = true)
    public boolean sensitiveWordFilterEnabled() {
        return booleanSetting(SENSITIVE_WORD_FILTER);
    }

    /** Admin "AI safety check" toggle, enforced by {@code AiSafetyGuard}. */
    @Transactional(readOnly = true)
    public boolean aiSafetyCheckEnabled() {
        return booleanSetting(AI_SAFETY_CHECK);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminAiSettingsResponse updateAiSettings(SecurityUserDetails currentUser, AdminAiSettingsUpdateRequest request) {
        User admin = currentAccount.lock(currentUser);
        Map<String, SystemSetting> existing = systemSettingRepository
                .findLockedBySettingKeyInOrderBySettingKeyAsc(AI_SETTING_KEYS).stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, Function.identity()));
        String notice = AI_DISCLAIMER_TEXT.valueOrDefault(request.aiNoticeText());
        upsertSetting(existing, AI_DISCLAIMER_TEXT, notice);
        upsertSetting(existing, SENSITIVE_WORD_FILTER, Boolean.toString(request.sensitiveWordFilter()));
        upsertSetting(existing, AI_SAFETY_CHECK, Boolean.toString(request.aiSafetyCheck()));

        AuditLog lastChange = auditLogService.record(admin, SETTINGS_UPDATED_ACTION, "SYSTEM_SETTING", null,
                "Admin updated the AI notice, sensitive-word filter and AI safety check settings.");
        return aiSettingsResponse(notice, request.sensitiveWordFilter(), request.aiSafetyCheck(), lastChange);
    }

    private void upsertSetting(Map<String, SystemSetting> existing, SettingDefinition definition, String value) {
        SystemSetting setting = existing.get(definition.key());
        if (setting == null) {
            setting = definition.newSetting();
        }
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }

    private String stringSetting(SettingDefinition definition) {
        String stored = systemSettingRepository.findValueByKey(definition.key()).orElse(null);
        return definition.valueOrDefault(stored);
    }

    /** On unless explicitly "false". */
    private boolean booleanSetting(SettingDefinition definition) {
        return !"false".equalsIgnoreCase(stringSetting(definition));
    }

    public record SettingDefinition(String key, String defaultValue) {

        private String valueOrDefault(String stored) {
            String value = TextUtils.trimToNull(stored);
            return value == null ? defaultValue : value;
        }

        public SystemSetting newSetting() {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(defaultValue);
            return setting;
        }
    }
}
