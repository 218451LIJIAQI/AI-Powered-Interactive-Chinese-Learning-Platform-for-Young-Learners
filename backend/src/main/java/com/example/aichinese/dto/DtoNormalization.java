package com.example.aichinese.dto;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Normalisation and check helpers shared by the request and response records. */
public final class DtoNormalization {

    /** An optional http(s) link without whitespace; an empty value means "no link". */
    public static final String OPTIONAL_HTTP_URL_PATTERN = "^(https?://\\S+)?$";

    private static final String REDACTED = "<redacted>";

    private DtoNormalization() {
    }

    public static String strip(String value) {
        return value == null ? null : value.strip();
    }

    public static String stripToNull(String value) {
        String stripped = strip(value);
        return stripped == null || stripped.isEmpty() ? null : stripped;
    }

    public static <T> List<T> immutableListOrEmpty(List<T> values) {
        return values == null ? List.of() : values.stream().toList();
    }

    /** The stripped value; a null or blank value is a programming error in the caller. */
    public static String requireNonBlank(String value, String fieldName) {
        String stripped = Objects.requireNonNull(value, fieldName + " must not be null").strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return stripped;
    }

    /** Role names stripped, without repeats, in their first order; an absent list is empty. */
    public static List<String> normalizeRoles(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>(source.size());
        for (String role : source) {
            normalized.add(requireNonBlank(role, "role"));
        }
        return List.copyOf(normalized);
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
