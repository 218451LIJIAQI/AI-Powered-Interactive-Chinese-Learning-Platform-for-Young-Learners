package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum TeacherStatus {

    PENDING,

    APPROVED,

    REJECTED;

    private static final Map<String, TeacherStatus> LOOKUP =
            EnumLookups.indexByName(TeacherStatus.class);

    public static Optional<TeacherStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static TeacherStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Teacher status is required.",
                () -> "Invalid teacher status. Allowed values are PENDING, APPROVED, or REJECTED.");
    }
}
