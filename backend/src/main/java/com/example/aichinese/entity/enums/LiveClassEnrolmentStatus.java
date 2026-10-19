package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum LiveClassEnrolmentStatus {

    ENROLLED,

    CANCELLED,

    ATTENDED,

    MISSED;

    private static final Map<String, LiveClassEnrolmentStatus> LOOKUP =
            EnumLookups.indexByName(LiveClassEnrolmentStatus.class);

    public static Optional<LiveClassEnrolmentStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static LiveClassEnrolmentStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Live class enrolment status is required.",
                () -> "Invalid live class enrolment status. Allowed values are ENROLLED, "
                        + "CANCELLED, ATTENDED, or MISSED.");
    }
}
