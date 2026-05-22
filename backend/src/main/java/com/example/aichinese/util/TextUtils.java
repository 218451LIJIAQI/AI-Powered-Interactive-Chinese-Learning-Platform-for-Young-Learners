package com.example.aichinese.util;

import java.util.Optional;

/**
 * Shared, side-effect-free helpers for normalising user-supplied text inputs.
 *
 * <p>Centralised here to eliminate the previous duplicated copies of the same
 * {@code trimToNull} / {@code defaultIfBlank} logic that lived inside several
 * services (AuthService, TeacherManagementService,
 * TeacherContentAccessService, AiService, AiAvailabilityService).</p>
 */
public final class TextUtils {

    private TextUtils() {
        // Utility class — not instantiable.
    }

    /**
     * Returns the trimmed value when the input is non-null and non-blank,
     * otherwise {@code null}. Treats whitespace-only strings as blank.
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
     */
    public static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
