package com.example.aichinese.entity.enums;

import java.util.Arrays;

public enum PackageItemType {
    MODULE,
    LESSON;

    public static PackageItemType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Package item type is required.");
        }

        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid package item type. Allowed values are MODULE or LESSON."));
    }
}
