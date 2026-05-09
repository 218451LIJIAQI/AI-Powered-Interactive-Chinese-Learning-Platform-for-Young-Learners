package com.example.aichinese.entity.enums;

import java.util.Arrays;

public enum PurchasableItemType {
    PACKAGE,
    COURSE,
    MODULE,
    PREMIUM_AI,
    SUBSCRIPTION,
    LESSON;

    public static PurchasableItemType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Purchase item type is required.");
        }

        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid purchase item type. Allowed values are PACKAGE, COURSE, MODULE, PREMIUM_AI, SUBSCRIPTION, or LESSON."));
    }
}
