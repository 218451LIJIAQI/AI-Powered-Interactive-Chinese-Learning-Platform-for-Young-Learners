package com.example.aichinese.util;

import java.util.Set;

/**
 * Interface languages supported by the platform: English ({@code en}), Bahasa Melayu
 * ({@code ms}) and Simplified Chinese ({@code zh-CN}, BCP-47).
 */
public final class LanguageCodes {

    public static final String ENGLISH = "en";

    public static final String MALAY = "ms";

    public static final String CHINESE_SIMPLIFIED = "zh-CN";

    /** Validation regex matching a supported interface language code (use with {@code stripToNull} when optional). */
    public static final String SUPPORTED_PATTERN = "^(en|ms|zh-CN)$";

    private static final int MAX_RAW_LANGUAGE_LENGTH = 32;
    private static final Set<String> SUPPORTED = Set.of(ENGLISH, MALAY, CHINESE_SIMPLIFIED);

    private LanguageCodes() {
    }

    /** The stripped value when it is a supported code; {@code null} when it is blank, oversized, or anything else. */
    public static String normalize(String value) {
        if (value == null || value.length() > MAX_RAW_LANGUAGE_LENGTH) {
            return null;
        }
        String trimmed = TextUtils.trimToNull(value);
        return trimmed != null && SUPPORTED.contains(trimmed) ? trimmed : null;
    }

    /** Same as {@link #normalize(String)} but falls back to English. */
    public static String normalizeOrDefault(String value) {
        String normalized = normalize(value);
        return normalized == null ? ENGLISH : normalized;
    }

    /** The student's current interface language (en, ms or zh-CN) wins; the account preference is the fallback. */
    public static String forRequest(String requestedLanguage, String accountLanguage) {
        String requested = normalize(requestedLanguage);
        return requested != null ? requested : normalizeOrDefault(accountLanguage);
    }
}
