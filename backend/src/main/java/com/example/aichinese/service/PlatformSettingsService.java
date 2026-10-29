package com.example.aichinese.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.config.CacheConfig;
import com.example.aichinese.dto.ai.AdminAiSettingsResponse;
import com.example.aichinese.dto.ai.AdminAiSettingsUpdateRequest;
import com.example.aichinese.dto.system.PublicPlatformSettingsResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ai.AiUsageLimiter;
import com.example.aichinese.util.TextUtils;

/**
 * Platform settings that take effect at runtime, edited on the admin AI Settings page:
 * <ul>
 *   <li>{@code ai_disclaimer_text} &ndash; the AI notice shown with every AI answer and on the Responsible AI page;</li>
 *   <li>{@code sensitive_word_filter_enabled} &ndash; enforced by {@link ContentSafetyService} for teacher content;</li>
 *   <li>{@code ai_safety_check_enabled} &ndash; enforced by {@code AiSafetyGuard} for student AI requests and replies.</li>
 * </ul>
 * The AI request limits are shown with them but come from the server configuration.
 */
@Service
public class PlatformSettingsService {

    public static final String AI_DISCLAIMER_TEXT_KEY = "ai_disclaimer_text";
    public static final String SENSITIVE_WORD_FILTER_KEY = "sensitive_word_filter_enabled";
    public static final String AI_SAFETY_CHECK_KEY = "ai_safety_check_enabled";
    /** The audit action of a settings change; the latest one is the settings' "last changed". */
    public static final String SETTINGS_UPDATED_ACTION = "ADMIN_PLATFORM_SETTINGS_UPDATED";

    public static final String DEFAULT_AI_DISCLAIMER_TEXT =
            "AI responses may contain errors and do not replace your teacher.";

    /** Every platform setting (all editable) with its default value and description, in seeding order. */
    public static final List<SettingDefinition> SETTING_DEFINITIONS = List.of(
            new SettingDefinition(
                    AI_DISCLAIMER_TEXT_KEY,
                    DEFAULT_AI_DISCLAIMER_TEXT,
                    "Shown with every AI answer and on the Responsible AI page."),
            new SettingDefinition(
                    SENSITIVE_WORD_FILTER_KEY,
                    "true",
                    "Block unsafe words in teacher-authored content."),
            new SettingDefinition(
                    AI_SAFETY_CHECK_KEY,
                    "true",
                    "Screen student AI requests and AI replies for unsafe content."));

    private final SystemSettingRepository systemSettingRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final AiProperties aiProperties;
    private final AiUsageLimiter aiUsageLimiter;

    public PlatformSettingsService(
            SystemSettingRepository systemSettingRepository,
            AuditLogRepository auditLogRepository,
            AuditLogService auditLogService,
            AiProperties aiProperties,
            AiUsageLimiter aiUsageLimiter) {
        this.systemSettingRepository = systemSettingRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.aiProperties = aiProperties;
        this.aiUsageLimiter = aiUsageLimiter;
    }

    @Transactional(readOnly = true)
    public AdminAiSettingsResponse getAiSettings() {
        AuditLog lastChange = auditLogRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("actionType"), SETTINGS_UPDATED_ACTION),
                PageRequest.of(0, 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .stream().findFirst().orElse(null);
        return new AdminAiSettingsResponse(
                stringSetting(AI_DISCLAIMER_TEXT_KEY),
                booleanSetting(SENSITIVE_WORD_FILTER_KEY),
                booleanSetting(AI_SAFETY_CHECK_KEY),
                aiUsageLimiter.requestsPerMinute(),
                aiUsageLimiter.requestsPerDay(),
                lastChange == null ? null : lastChange.getCreatedAt(),
                lastChange == null ? null : actorName(lastChange));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PUBLIC_PLATFORM_SETTINGS)
    public PublicPlatformSettingsResponse getPublicPlatformSettings() {
        return new PublicPlatformSettingsResponse(
                stringSetting(AI_DISCLAIMER_TEXT_KEY),
                TextUtils.trimToNull(aiProperties.getModelName()));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PUBLIC_PLATFORM_SETTINGS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SENSITIVE_WORD_FILTER_ENABLED, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.AI_SAFETY_CHECK_ENABLED, allEntries = true)
    })
    public AdminAiSettingsResponse updateAiSettings(SecurityUserDetails currentUser, AdminAiSettingsUpdateRequest request) {
        upsertSetting(AI_DISCLAIMER_TEXT_KEY, request.aiNoticeText());
        upsertSetting(SENSITIVE_WORD_FILTER_KEY, Boolean.toString(request.sensitiveWordFilter()));
        upsertSetting(AI_SAFETY_CHECK_KEY, Boolean.toString(request.aiSafetyCheck()));

        auditLogService.record(currentUser, SETTINGS_UPDATED_ACTION, "SYSTEM_SETTING", null,
                "Admin updated the AI notice, sensitive-word filter and AI safety check settings.");
        return getAiSettings();
    }

    private static String actorName(AuditLog entry) {
        return entry.getActorUser() != null ? entry.getActorUser().getDisplayName() : entry.getActorEmail();
    }

    private void upsertSetting(String key, String value) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseGet(() -> definitionFor(key).newSetting());
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }

    private String stringSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .map(TextUtils::trimToNull)
                .orElseGet(() -> definitionFor(key).defaultValue());
    }

    /** On unless explicitly "false", the rule ContentSafetySettingsReader enforces. */
    private boolean booleanSetting(String key) {
        return !"false".equalsIgnoreCase(stringSetting(key));
    }

    private SettingDefinition definitionFor(String key) {
        return SETTING_DEFINITIONS.stream()
                .filter(definition -> definition.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported system setting key: " + key));
    }

    public record SettingDefinition(String key, String defaultValue, String description) {

        public SystemSetting newSetting() {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(defaultValue);
            setting.setDescription(description);
            return setting;
        }
    }
}
