package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum AiResponseReportReason {

    INCORRECT_CONTENT,

    HALLUCINATION,

    UNSAFE_CONTENT,

    INAPPROPRIATE_CONTENT,

    OTHER;

    private static final Map<String, AiResponseReportReason> LOOKUP =
            EnumLookups.indexByName(AiResponseReportReason.class);

    public static Optional<AiResponseReportReason> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static AiResponseReportReason fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI report reason is required.",
                () -> "Invalid AI report reason. Allowed values are INCORRECT_CONTENT, "
                        + "HALLUCINATION, UNSAFE_CONTENT, INAPPROPRIATE_CONTENT, or OTHER.");
    }
}
