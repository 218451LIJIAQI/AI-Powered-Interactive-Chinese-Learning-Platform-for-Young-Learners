package com.example.aichinese.util;

import java.util.Locale;

/**
 * Interface languages supported by the platform: English ({@code en}), Bahasa Melayu
 * ({@code ms}) and Simplified Chinese ({@code zh-CN}, BCP-47).
 *
 * <p>The legacy code {@code zh} (and variants such as {@code zh_cn} or {@code zh-Hans})
 * is still accepted and mapped to {@code zh-CN}.</p>
 */
public final class LanguageCodes {

    public static final String ENGLISH = "en";

    public static final String MALAY = "ms";

    public static final String CHINESE_SIMPLIFIED = "zh-CN";

    private LanguageCodes() {
    }

    /**
     * Maps a requested language tag to a supported code.
     *
     * @return {@code en}, {@code ms} or {@code zh-CN}; {@code null} when the value is blank
     *         or not one of the supported languages.
     */
    public static String normalize(String value) {
        String trimmed = TextUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("en") || lower.startsWith("en-") || lower.startsWith("en_")) {
            return ENGLISH;
        }
        if (lower.equals("ms") || lower.startsWith("ms-") || lower.startsWith("ms_")) {
            return MALAY;
        }
        if (lower.equals("zh") || lower.startsWith("zh-") || lower.startsWith("zh_")) {
            return CHINESE_SIMPLIFIED;
        }
        return null;
    }

    /** Same as {@link #normalize(String)} but falls back to English. */
    public static String normalizeOrDefault(String value) {
        String normalized = normalize(value);
        return normalized == null ? ENGLISH : normalized;
    }
}
