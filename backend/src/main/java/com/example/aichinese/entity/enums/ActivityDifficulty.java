package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum ActivityDifficulty {

    EASY,

    MEDIUM,

    HARD;

    private static final Map<String, ActivityDifficulty> LOOKUP =
            EnumLookups.indexByName(ActivityDifficulty.class);

    public static Optional<ActivityDifficulty> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static ActivityDifficulty fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Activity difficulty is required.",
                () -> "Invalid activity difficulty. Allowed values are EASY, MEDIUM, or HARD.");
    }
}
