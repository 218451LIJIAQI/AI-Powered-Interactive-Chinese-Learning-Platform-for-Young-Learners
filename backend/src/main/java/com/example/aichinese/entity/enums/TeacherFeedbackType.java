package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum TeacherFeedbackType {

    GENERAL,

    QUIZ,

    LESSON,

    ENCOURAGING,

    CORRECTIVE,

    PROGRESS,

    WEAK_AREA;

    private static final Map<String, TeacherFeedbackType> LOOKUP =
            EnumLookups.indexByName(TeacherFeedbackType.class);

    public static Optional<TeacherFeedbackType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static TeacherFeedbackType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Teacher feedback type is required.",
                () -> "Invalid teacher feedback type. Allowed values are GENERAL, QUIZ, LESSON, "
                        + "ENCOURAGING, CORRECTIVE, PROGRESS, or WEAK_AREA.");
    }

    public static TeacherFeedbackType fromValueOrGeneral(String value) {
        return tryFrom(value).orElse(GENERAL);
    }

    public boolean isPositive() {
        return this == ENCOURAGING || this == PROGRESS;
    }

    public boolean isCorrective() {
        return this == CORRECTIVE || this == WEAK_AREA;
    }
}
