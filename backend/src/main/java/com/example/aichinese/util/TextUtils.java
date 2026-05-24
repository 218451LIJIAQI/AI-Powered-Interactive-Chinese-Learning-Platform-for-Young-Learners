package com.example.aichinese.util;

import java.util.Optional;

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
}
