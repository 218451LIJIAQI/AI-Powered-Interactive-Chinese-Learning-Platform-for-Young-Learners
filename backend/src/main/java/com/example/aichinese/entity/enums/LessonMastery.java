package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Derived per-lesson mastery indicator (D25, FR-PROG-04); not persisted. MASTERED = lesson completed and best quiz percentage of at least 80 percent.
 */
public enum LessonMastery {

    NOT_STARTED,

    IN_PROGRESS,

    COMPLETED,

    MASTERED;

    private static final Map<String, LessonMastery> LOOKUP =
            EnumLookups.indexByName(LessonMastery.class);

    public static Optional<LessonMastery> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static LessonMastery fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Mastery is required.",
                () -> "Invalid value. Allowed values are NOT_STARTED, IN_PROGRESS, COMPLETED, MASTERED.");
    }
}
