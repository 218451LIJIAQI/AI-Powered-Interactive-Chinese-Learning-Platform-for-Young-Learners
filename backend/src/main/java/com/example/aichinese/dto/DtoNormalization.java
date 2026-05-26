package com.example.aichinese.dto;

import java.util.List;

/**
 * Small boundary-normalisation helpers for request DTO canonical
 * constructors.
 *
 * <p>DTO constructors run before Jakarta Bean Validation. Keeping these
 * operations here makes the validation contract consistent: surrounding
 * whitespace is removed before {@code @Size} checks, optional blank values
 * collapse to {@code null}, and caller-owned mutable lists cannot be changed
 * after a request object has been constructed.</p>
 */
public final class DtoNormalization {

    private DtoNormalization() {
        throw new UnsupportedOperationException("DtoNormalization is a utility class.");
    }

    /**
     * Strips leading/trailing Unicode whitespace while preserving
     * {@code null}. Use for required string fields so {@code @NotBlank}
     * can still report the friendly validation error.
     */
    public static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /**
     * Strips leading/trailing Unicode whitespace and returns {@code null}
     * for blank optional values.
     */
    public static String stripToNull(String value) {
        String stripped = strip(value);
        return stripped == null || stripped.isEmpty() ? null : stripped;
    }

    /**
     * Returns an unmodifiable defensive copy while preserving
     * {@code null} as "not supplied". Null elements are intentionally
     * preserved so Bean Validation can report element-level violations.
     */
    public static <T> List<T> immutableListOrNull(List<T> values) {
        return values == null ? null : values.stream().toList();
    }

    /**
     * Returns an unmodifiable defensive copy, treating a missing list as
     * empty. Null elements are intentionally preserved so Bean Validation
     * can report element-level violations.
     */
    public static <T> List<T> immutableListOrEmpty(List<T> values) {
        return values == null ? List.of() : values.stream().toList();
    }

    /**
     * Strips every non-null string element and returns an unmodifiable
     * defensive copy while preserving a {@code null} list as "not supplied".
     */
    public static List<String> strippedStringListOrNull(List<String> values) {
        return values == null ? null : values.stream()
                .map(DtoNormalization::strip)
                .toList();
    }
}
