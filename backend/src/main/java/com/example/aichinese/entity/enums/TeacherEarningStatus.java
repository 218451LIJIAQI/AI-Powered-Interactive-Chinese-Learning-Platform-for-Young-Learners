package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum TeacherEarningStatus {

    AVAILABLE,

    REFUNDED;

    private static final Map<String, TeacherEarningStatus> LOOKUP =
            EnumLookups.indexByName(TeacherEarningStatus.class);

    public static Optional<TeacherEarningStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static TeacherEarningStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Teacher earning status is required.",
                () -> "Invalid teacher earning status. Allowed values are AVAILABLE or REFUNDED.");
    }
}
