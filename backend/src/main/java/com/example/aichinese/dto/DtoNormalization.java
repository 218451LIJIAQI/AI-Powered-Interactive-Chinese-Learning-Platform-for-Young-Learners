package com.example.aichinese.dto;

import java.util.List;

import com.example.aichinese.util.TextUtils;

/** Normalisation and {@code toString()} helpers shared by the request and response records. */
public final class DtoNormalization {

    private static final String REDACTED = "<redacted>";

    private DtoNormalization() {
    }

    /** Strip form whitespace before validation, preserving an omitted field for partial updates. */
    public static String strip(String value) {
        return value == null ? null : TextUtils.stripFormSpaces(value);
    }

    /** Strip form whitespace (including NBSP / full-width spaces) and convert blank optional fields to {@code null}. */
    public static String stripToNull(String value) {
        return TextUtils.trimToNull(value);
    }

    /** Leaves null elements for Bean Validation to report at their actual list positions. */
    public static <T> List<T> immutableListOrEmpty(List<T> values) {
        return values == null ? List.of() : values.stream().toList();
    }

    /** Preserves {@code null} so {@code @NotNull} rejects missing lists, while copying non-null lists immutably. */
    public static <T> List<T> immutableListOrNull(List<T> values) {
        return values == null ? null : values.stream().toList();
    }

    /** For toString(): hides a personal value but shows whether it is present. */
    public static String redactValue(String value) {
        return value == null ? "null" : REDACTED;
    }

    /** For toString(): hides free text but keeps its length. */
    public static String redactLength(String value) {
        return value == null ? "null" : "<redacted:" + value.length() + ">";
    }
}
