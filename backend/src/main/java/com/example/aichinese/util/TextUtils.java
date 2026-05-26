package com.example.aichinese.util;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Shared, side-effect-free helpers for normalising user-supplied text inputs.
 *
 * <p>Centralised here to eliminate the previous duplicated copies of the same
 * {@code trimToNull} / {@code defaultIfBlank} logic that lived inside several
 * services (AuthService, TeacherManagementService,
 * TeacherContentAccessService, AiService, AiAvailabilityService).</p>
 *
 * <h2>Unicode-aware whitespace</h2>
 * <p>The strip-style helpers below use {@link String#strip()} rather than
 * {@link String#trim()} so that ideographic spaces ({@code U+3000}) and
 * other Unicode whitespace characters routinely produced by CJK IMEs are
 * treated as blank — relevant for a Chinese-learning platform where
 * pasted lesson content frequently contains fullwidth whitespace.</p>
 */
public final class TextUtils {

    private static final String ELLIPSIS = "...";

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\p{javaWhitespace}+");

    private TextUtils() {
        throw new AssertionError("TextUtils is a utility class and must not be instantiated.");
    }

    /**
     * Returns the stripped value when the input is non-null and non-blank,
     * otherwise {@code null}. Treats Unicode whitespace as blank (see
     * class-level Javadoc for the rationale).
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * Optional-returning variant of {@link #trimToNull(String)}, useful when
     * the caller wants to chain {@code .map} / {@code .orElseThrow}.
     */
    public static Optional<String> trimToOptional(String value) {
        return Optional.ofNullable(trimToNull(value));
    }

    /**
     * Returns {@code value} when it is non-null and non-blank; otherwise the
     * supplied {@code fallback}. {@code value} is returned as-is (whitespace
     * preserved) so this is appropriate for downstream rendering where the
     * caller has already normalised the string. Use {@link #trimToNull(String)}
     * first if the input may contain unwanted leading/trailing whitespace.
     *
     * <p>"Blank" is determined via {@link String#isBlank()}, which is itself
     * Unicode-whitespace-aware so a value consisting only of ideographic
     * spaces correctly falls back.</p>
     */
    public static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Collapses every Unicode whitespace run to one ASCII space, strips the
     * result, and returns {@code null} when no text remains. This is useful for
     * log-safe / UI-safe one-line summaries where multi-line input should not
     * leak layout control into the response.
     */
    public static String collapseWhitespaceToNull(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = WHITESPACE_RUN.matcher(value).replaceAll(" ").strip();
        return collapsed.isEmpty() ? null : collapsed;
    }

    /**
     * Truncates {@code value} to at most {@code maxLength} UTF-16 code units
     * without splitting a surrogate pair. A {@code null} value remains
     * {@code null}.
     *
     * @throws IllegalArgumentException if {@code maxLength} is less than 1
     */
    public static String safeTruncate(String value, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive.");
        }
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int seam = maxLength;
        if (Character.isHighSurrogate(value.charAt(seam - 1))
                && seam < value.length()
                && Character.isLowSurrogate(value.charAt(seam))) {
            seam--;
        }
        return value.substring(0, seam);
    }

    /**
     * Produces a stripped, single-line summary capped at {@code maxLength}
     * characters including the trailing ellipsis. Returns {@code null} for
     * {@code null} or blank input.
     */
    public static String truncateWithEllipsis(String value, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive.");
        }
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
}
