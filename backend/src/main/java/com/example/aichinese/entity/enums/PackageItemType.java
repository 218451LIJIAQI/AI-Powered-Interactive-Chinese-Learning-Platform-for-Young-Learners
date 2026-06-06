package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum PackageItemType {

    MODULE,

    LESSON;

    private static final Map<String, PackageItemType> LOOKUP =
            EnumLookups.indexByName(PackageItemType.class);

    public static Optional<PackageItemType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static PackageItemType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Package item type is required.",
                () -> "Invalid package item type. Allowed values are MODULE or LESSON.");
    }
}
