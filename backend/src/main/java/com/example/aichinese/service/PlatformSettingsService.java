package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.CacheConfig;
import com.example.aichinese.dto.system.AdminPlatformSettingsResponse;
import com.example.aichinese.dto.system.AdminPlatformSettingsUpdateRequest;
import com.example.aichinese.dto.system.PublicPlatformSettingsResponse;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.TextUtils;

/**
 * Platform settings that take effect at runtime (decisions D16/D27):
 * <ul>
 *   <li>{@code platform_name} &ndash; read-only display name;</li>
 *   <li>{@code ai_disclaimer_text} &ndash; shown next to every AI feature;</li>
 *   <li>{@code sensitive_word_filter_enabled} &ndash; enforced by {@link ContentSafetyService} for teacher content;</li>
 *   <li>{@code ai_safety_check_enabled} &ndash; enforced by {@code AiSafetyGuard} for learner AI requests and replies.</li>
 * </ul>
 * Earlier cosmetic toggles (admin e-mail / in-app notifications, daily summary, strict content
 * filtering) were never enforced and were removed (V3 migration deletes their rows).
 */
@Service
public class PlatformSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSettingsService.class);

    public static final String PLATFORM_NAME_KEY = "platform_name";
    public static final String AI_DISCLAIMER_TEXT_KEY = "ai_disclaimer_text";
    public static final String SENSITIVE_WORD_FILTER_KEY = "sensitive_word_filter_enabled";
    public static final String AI_SAFETY_CHECK_KEY = "ai_safety_check_enabled";

    public static final String DEFAULT_PLATFORM_NAME =
            "AI-Powered Interactive Chinese Learning Platform for Young Adults in Malaysia";
    public static final String DEFAULT_AI_DISCLAIMER_TEXT =
            "AI responses may contain errors and do not replace your teacher.";

    private final SystemSettingRepository systemSettingRepository;
    private final AuditLogService auditLogService;

    public PlatformSettingsService(
            SystemSettingRepository systemSettingRepository,
            AuditLogService auditLogService) {
        this.systemSettingRepository = Objects.requireNonNull(systemSettingRepository, "systemSettingRepository");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.ADMIN_PLATFORM_SETTINGS)
    public AdminPlatformSettingsResponse getAdminPlatformSettings() {
        return new AdminPlatformSettingsResponse(
                stringSetting(PLATFORM_NAME_KEY),
                stringSetting(AI_DISCLAIMER_TEXT_KEY),
                booleanSetting(SENSITIVE_WORD_FILTER_KEY),
                booleanSetting(AI_SAFETY_CHECK_KEY));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PUBLIC_PLATFORM_SETTINGS)
    public PublicPlatformSettingsResponse getPublicPlatformSettings() {
        return new PublicPlatformSettingsResponse(
                stringSetting(PLATFORM_NAME_KEY),
                stringSetting(AI_DISCLAIMER_TEXT_KEY),
                booleanSetting(SENSITIVE_WORD_FILTER_KEY),
                booleanSetting(AI_SAFETY_CHECK_KEY));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.ADMIN_PLATFORM_SETTINGS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.PUBLIC_PLATFORM_SETTINGS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SENSITIVE_WORD_FILTER_ENABLED, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.AI_SAFETY_CHECK_ENABLED, allEntries = true)
    })
    public AdminPlatformSettingsResponse updateAdminPlatformSettings(
            SecurityUserDetails currentUser,
            AdminPlatformSettingsUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Platform settings update request is required.");
        }
        String platformName = stringSetting(PLATFORM_NAME_KEY);
        String normalizedDisclaimerText = TextUtils.trimToNull(request.aiDisclaimerText());
        if (normalizedDisclaimerText == null) {
            throw new ResponseStatusException(BAD_REQUEST, "AI disclaimer text is required.");
        }

        upsertSetting(AI_DISCLAIMER_TEXT_KEY, normalizedDisclaimerText);
        upsertSetting(SENSITIVE_WORD_FILTER_KEY, Boolean.toString(request.sensitiveWordFilter()));
        upsertSetting(AI_SAFETY_CHECK_KEY, Boolean.toString(request.aiSafetyCheck()));

        log.info("Platform settings updated by adminUserId={}", currentUser.getUserId());
        auditLogService.record(
                currentUser,
                "ADMIN_PLATFORM_SETTINGS_UPDATED",
                "SYSTEM_SETTING",
                null,
                "Admin updated the AI disclaimer, sensitive-word filter and AI safety check settings.");

        return new AdminPlatformSettingsResponse(
                platformName,
                normalizedDisclaimerText,
                request.sensitiveWordFilter(),
                request.aiSafetyCheck());
    }

    private void upsertSetting(String key, String value) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseGet(() -> newSetting(key));
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }

    private SystemSetting newSetting(String key) {
        SettingDefinition definition = definitionFor(key);
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(definition.defaultValue());
        setting.setDescription(definition.description());
        setting.setEditable(definition.editable());
        return setting;
    }

    private String stringSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .map(TextUtils::trimToNull)
                .orElseGet(() -> definitionFor(key).defaultValue());
    }

    private boolean booleanSetting(String key) {
        return Boolean.parseBoolean(stringSetting(key));
    }

    private SettingDefinition definitionFor(String key) {
        return switch (key) {
            case PLATFORM_NAME_KEY -> new SettingDefinition(
                    DEFAULT_PLATFORM_NAME,
                    "Displayed platform name.",
                    false);
            case AI_DISCLAIMER_TEXT_KEY -> new SettingDefinition(
                    DEFAULT_AI_DISCLAIMER_TEXT,
                    "Shown next to every AI-powered learning feature.",
                    true);
            case SENSITIVE_WORD_FILTER_KEY -> new SettingDefinition(
                    "true",
                    "Block unsafe words in teacher-authored content.",
                    true);
            case AI_SAFETY_CHECK_KEY -> new SettingDefinition(
                    "true",
                    "Screen learner AI requests and AI replies for unsafe content.",
                    true);
            default -> throw new IllegalArgumentException("Unsupported system setting key: " + key);
        };
    }

    private record SettingDefinition(
            String defaultValue,
            String description,
            boolean editable) {
    }
}
