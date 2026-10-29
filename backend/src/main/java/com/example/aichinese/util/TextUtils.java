package com.example.aichinese.util;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Helpers for normalising user-supplied text. They use {@link String#strip()}, not {@link String#trim()}, so the
 * ideographic space ({@code U+3000}) and other Unicode whitespace that CJK input methods produce count as blank.
 */
public final class TextUtils {

    private static final String ELLIPSIS = "...";

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\p{javaWhitespace}+");

    private TextUtils() {
    }

    /** The stripped value, or {@code null} when it is null or blank. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    public static Optional<String> trimToOptional(String value) {
        return Optional.ofNullable(trimToNull(value));
    }

    /** {@code value} unchanged (not stripped) unless it is null or blank, then {@code fallback}. */
    public static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * One line: every whitespace run becomes one space and the result is stripped; {@code null} when no text remains.
     * For summaries where multi-line input must not carry its layout into a log or a response.
     */
    public static String collapseWhitespaceToNull(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = WHITESPACE_RUN.matcher(value).replaceAll(" ").strip();
        return collapsed.isEmpty() ? null : collapsed;
    }

    /**
     * At most {@code maxLength} UTF-16 code units, never splitting a surrogate pair; {@code null} stays {@code null}.
     *
     * @throws IllegalArgumentException if {@code maxLength} is less than 1
     */
    public static String safeTruncate(String value, int maxLength) {
        requirePositiveMaxLength(maxLength);
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int seam = truncationEnd(value, maxLength);
        return value.substring(0, seam);
    }

    /**
     * A one-line summary (see {@link #collapseWhitespaceToNull}) of at most {@code maxLength} characters, the
     * trailing ellipsis included; {@code null} for null or blank input.
     */
    public static String truncateWithEllipsis(String value, int maxLength) {
        requirePositiveMaxLength(maxLength);
        String normalized = collapseWhitespaceToNull(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= ELLIPSIS.length()) {
            return safeTruncate(ELLIPSIS, maxLength);
        }
        int contentLength = maxLength - ELLIPSIS.length();
        String truncated = safeTruncate(normalized, contentLength).stripTrailing();
        return truncated.isEmpty() ? safeTruncate(ELLIPSIS, maxLength) : truncated + ELLIPSIS;
    }

    private static void requirePositiveMaxLength(int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive.");
        }
    }

    private static int truncationEnd(String value, int maxLength) {
        return splitsSurrogatePair(value, maxLength) ? maxLength - 1 : maxLength;
    }

    private static boolean splitsSurrogatePair(String value, int seam) {
        return Character.isHighSurrogate(value.charAt(seam - 1))
                && seam < value.length()
                && Character.isLowSurrogate(value.charAt(seam));
    }
}
