package com.example.aichinese.config;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The platform time zone: the day boundary of streaks, daily goals and AI quotas. */
@ConfigurationProperties(prefix = "app.platform")
public record PlatformProperties(String defaultTimeZone) {

    private static final String DEFAULT_TIME_ZONE = "Asia/Singapore";

    public PlatformProperties {
        defaultTimeZone = normalizeTimeZone(defaultTimeZone);
    }

    private static String normalizeTimeZone(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_TIME_ZONE : value.strip();
        try {
            return ZoneId.of(normalized).getId();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("APP_DEFAULT_TIME_ZONE must be a valid IANA time zone.", exception);
        }
    }
}
