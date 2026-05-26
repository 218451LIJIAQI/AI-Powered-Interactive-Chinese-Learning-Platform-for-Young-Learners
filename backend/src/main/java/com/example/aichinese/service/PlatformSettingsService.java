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

import com.example.aichinese.config.CacheConfig;
import com.example.aichinese.dto.system.AdminPlatformSettingsResponse;
import com.example.aichinese.dto.system.AdminPlatformSettingsUpdateRequest;
import com.example.aichinese.dto.system.PublicPlatformSettingsResponse;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads and writes the admin-editable platform-wide settings backed
 * by {@link SystemSetting}: platform name, AI disclaimer text,
 * admin-notification toggles, and the content-filtering switches.
 *
 * <h2>Two response shapes</h2>
 * <p>Admins see the full {@link AdminPlatformSettingsResponse} with
 * every editable key; the public-facing
 * {@link PublicPlatformSettingsResponse} only exposes display-tier
 * values (platform name, AI disclaimer) so the front-end can render
 * branding / disclaimers without leaking operational toggles to
 * unauthenticated visitors.</p>
 *
 * <h2>Defaults</h2>
 * <p>Missing rows fall back to the {@code DEFAULT_*} constants so a
 * fresh deployment renders coherent text from day one. Updates audit
 * via {@link AuditLogService}.</p>
 */
@Service
public class PlatformSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSettingsService.class);

    public static final String PLATFORM_NAME_KEY = "platform_name";
    public static final String AI_DISCLAIMER_TEXT_KEY = "ai_disclaimer_text";
    public static final String EMAIL_NOTIFICATIONS_KEY = "admin_email_notifications_enabled";
    public static final String IN_APP_NOTIFICATIONS_KEY = "admin_in_app_notifications_enabled";
    public static final String DAILY_SUMMARY_KEY = "admin_daily_summary_enabled";
    public static final String STRICT_CONTENT_FILTERING_KEY = "strict_content_filtering_enabled";
    public static final String SENSITIVE_WORD_FILTER_KEY = "sensitive_word_filter_enabled";
    public static final String AI_SAFETY_CHECK_KEY = "ai_safety_check_enabled";

    private static final String DEFAULT_PLATFORM_NAME = "AI-Powered Interactive Chinese Learning Platform for Young Learners";
    private static final String DEFAULT_AI_DISCLAIMER_TEXT = "AI-generated content may not always be accurate. Please review before use.";

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
                booleanSetting(EMAIL_NOTIFICATIONS_KEY),
                booleanSetting(IN_APP_NOTIFICATIONS_KEY),
                booleanSetting(DAILY_SUMMARY_KEY),
                booleanSetting(STRICT_CONTENT_FILTERING_KEY),
                booleanSetting(SENSITIVE_WORD_FILTER_KEY),
                booleanSetting(AI_SAFETY_CHECK_KEY));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PUBLIC_PLATFORM_SETTINGS)
    public PublicPlatformSettingsResponse getPublicPlatformSettings() {
        return new PublicPlatformSettingsResponse(
                stringSetting(PLATFORM_NAME_KEY),
                stringSetting(AI_DISCLAIMER_TEXT_KEY),
                booleanSetting(STRICT_CONTENT_FILTERING_KEY),
                booleanSetting(SENSITIVE_WORD_FILTER_KEY),
                booleanSetting(AI_SAFETY_CHECK_KEY));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.ADMIN_PLATFORM_SETTINGS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.PUBLIC_PLATFORM_SETTINGS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SENSITIVE_WORD_FILTER_ENABLED, allEntries = true)
    })
    public AdminPlatformSettingsResponse updateAdminPlatformSettings(
            SecurityUserDetails currentUser,
            AdminPlatformSettingsUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Platform settings update request is required.");
        }
        String platformName = stringSetting(PLATFORM_NAME_KEY);
        String normalizedDisclaimerText = request.aiDisclaimerText().trim();

        upsertSetting(AI_DISCLAIMER_TEXT_KEY, normalizedDisclaimerText);
        upsertSetting(EMAIL_NOTIFICATIONS_KEY, Boolean.toString(request.emailNotifications()));
        upsertSetting(IN_APP_NOTIFICATIONS_KEY, Boolean.toString(request.inAppNotifications()));
        upsertSetting(DAILY_SUMMARY_KEY, Boolean.toString(request.dailySummary()));
        upsertSetting(STRICT_CONTENT_FILTERING_KEY, Boolean.toString(request.strictContentFiltering()));
        upsertSetting(SENSITIVE_WORD_FILTER_KEY, Boolean.toString(request.sensitiveWordFilter()));
        upsertSetting(AI_SAFETY_CHECK_KEY, Boolean.toString(request.aiSafetyCheck()));

        log.info("Platform settings updated by adminUserId={}", currentUser.getUserId());
        auditLogService.record(
                currentUser.getUser(),
                "ADMIN_PLATFORM_SETTINGS_UPDATED",
                "SYSTEM_SETTING",
                null,
                "Admin updated platform AI, notification, and safety settings.");

        return new AdminPlatformSettingsResponse(
                platformName,
                normalizedDisclaimerText,
                request.emailNotifications(),
                request.inAppNotifications(),
                request.dailySummary(),
                request.strictContentFiltering(),
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
                .filter(value -> !value.isBlank())
                .map(String::trim)
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
                    "Shown on AI-powered learning experiences.",
                    true);
            case EMAIL_NOTIFICATIONS_KEY -> new SettingDefinition(
                    "true",
                    "Enable email notifications for admin workflows.",
                    true);
            case IN_APP_NOTIFICATIONS_KEY -> new SettingDefinition(
                    "true",
                    "Enable in-app notifications for admin workflows.",
                    true);
            case DAILY_SUMMARY_KEY -> new SettingDefinition(
                    "false",
                    "Enable daily admin summary delivery.",
                    true);
            case STRICT_CONTENT_FILTERING_KEY -> new SettingDefinition(
                    "true",
                    "Enable strict content filtering for safety-sensitive flows.",
                    true);
            case SENSITIVE_WORD_FILTER_KEY -> new SettingDefinition(
                    "true",
                    "Enable profanity and sensitive word filtering.",
                    true);
            case AI_SAFETY_CHECK_KEY -> new SettingDefinition(
                    "true",
                    "Enable AI safety checks before AI guidance is shown.",
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
