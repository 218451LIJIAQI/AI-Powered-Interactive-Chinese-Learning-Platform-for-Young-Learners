package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum ManagedContentType {

    MODULE,

    LESSON,

    PACKAGE;

    private static final Map<String, ManagedContentType> LOOKUP =
            EnumLookups.indexByName(ManagedContentType.class);

    public static Optional<ManagedContentType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static ManagedContentType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Content type is required.",
                () -> "Invalid content type. Allowed values are MODULE, LESSON, or PACKAGE.");
    }
}
