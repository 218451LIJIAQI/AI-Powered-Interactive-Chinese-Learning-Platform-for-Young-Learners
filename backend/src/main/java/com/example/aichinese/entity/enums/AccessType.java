package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum AccessType {

    FREE,

    PAID;

    private static final Map<String, AccessType> LOOKUP = EnumLookups.indexByName(AccessType.class);

    public static Optional<AccessType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static AccessType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Access type is required.",
                () -> "Invalid access type. Allowed values are FREE or PAID.");
    }

    public boolean isFree() {
        return this == FREE;
    }

    public boolean isPaid() {
        return this == PAID;
    }
}
