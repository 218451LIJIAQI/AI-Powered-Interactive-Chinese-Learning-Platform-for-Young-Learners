package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Outcome of the optional AI-assisted feedback stored with a practice submission (D7a); never a score.
 */
public enum PracticeAiFeedbackStatus {

    AVAILABLE,

    UNAVAILABLE,

    FAILED;

    private static final Map<String, PracticeAiFeedbackStatus> LOOKUP =
            EnumLookups.indexByName(PracticeAiFeedbackStatus.class);

    public static Optional<PracticeAiFeedbackStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static PracticeAiFeedbackStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI feedback status is required.",
                () -> "Invalid value. Allowed values are AVAILABLE, UNAVAILABLE, FAILED.");
    }
}
