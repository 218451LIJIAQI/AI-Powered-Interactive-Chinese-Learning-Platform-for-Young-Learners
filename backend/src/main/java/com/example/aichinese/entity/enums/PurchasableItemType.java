package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum PurchasableItemType {

    PACKAGE,

    COURSE,

    MODULE,

    PREMIUM_AI,

    LESSON;

    private static final Map<String, PurchasableItemType> LOOKUP =
            EnumLookups.indexByName(PurchasableItemType.class);

    public static Optional<PurchasableItemType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static PurchasableItemType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Purchase item type is required.",
                () -> "Invalid purchase item type. Allowed values are PACKAGE, COURSE, "
                        + "MODULE, PREMIUM_AI, or LESSON.");
    }
}
