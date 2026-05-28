package com.example.aichinese.config;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-cutting deployment settings that affect data semantics and safe
 * runtime topology.
 */
@ConfigurationProperties(prefix = "app.platform")
public record PlatformProperties(
        String defaultTimeZone,
        String deploymentMode,
        boolean sharedAuthRateLimitingEnabled,
        boolean localStorageMultiInstanceAcknowledged) {

    private static final String DEFAULT_TIME_ZONE = "Asia/Singapore";
    private static final String SINGLE_INSTANCE = "single-instance";
    private static final String MULTI_INSTANCE = "multi-instance";

    public PlatformProperties {
        defaultTimeZone = normalizeTimeZone(defaultTimeZone);
        deploymentMode = normalizeDeploymentMode(deploymentMode);
    }

    public ZoneId defaultZoneId() {
        return ZoneId.of(defaultTimeZone);
    }

    public boolean isMultiInstance() {
        return MULTI_INSTANCE.equals(deploymentMode);
    }

    private static String normalizeTimeZone(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_TIME_ZONE : value.strip();
        try {
            return ZoneId.of(normalized).getId();
        } catch (ZoneRulesException exception) {
            throw new IllegalArgumentException("APP_DEFAULT_TIME_ZONE must be a valid IANA time zone.", exception);
        }
    }

    private static String normalizeDeploymentMode(String value) {
        String normalized = value == null || value.isBlank()
                ? SINGLE_INSTANCE
                : value.strip().toLowerCase(Locale.ROOT);
        if (!SINGLE_INSTANCE.equals(normalized) && !MULTI_INSTANCE.equals(normalized)) {
            throw new IllegalArgumentException("APP_DEPLOYMENT_MODE must be single-instance or multi-instance.");
        }
        return normalized;
    }
}
