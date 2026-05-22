package com.example.aichinese.entity.enums;

import java.util.Arrays;

public enum ManagedContentType {
    MODULE,
    LESSON,
    PACKAGE;

    public static ManagedContentType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Content type is required.");
        }

        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid content type. Allowed values are MODULE, LESSON, or PACKAGE."));
    }
}
