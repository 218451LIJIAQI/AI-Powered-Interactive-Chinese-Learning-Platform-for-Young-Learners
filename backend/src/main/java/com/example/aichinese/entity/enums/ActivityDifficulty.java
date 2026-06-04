package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum ActivityDifficulty {

    EASY(1.0d),

    MEDIUM(1.5d),

    HARD(2.0d);

    private final double xpMultiplier;

    ActivityDifficulty(double xpMultiplier) {
        if (!(xpMultiplier > 0.0d) || Double.isInfinite(xpMultiplier)) {
            throw new IllegalArgumentException(
                    "xpMultiplier must be a finite positive number; got " + xpMultiplier);
        }
        this.xpMultiplier = xpMultiplier;
    }

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

    public double xpMultiplier() {
        return xpMultiplier;
    }
}
