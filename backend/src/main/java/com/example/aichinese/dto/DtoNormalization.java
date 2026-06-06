package com.example.aichinese.dto;

import java.util.List;

public final class DtoNormalization {

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

    public static List<String> strippedStringListOrNull(List<String> values) {
        return values == null ? null : values.stream()
                .map(DtoNormalization::strip)
                .toList();
    }
}
