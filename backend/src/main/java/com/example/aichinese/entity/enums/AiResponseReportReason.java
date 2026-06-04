package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum AiResponseReportReason {

    INCORRECT_CONTENT,

    HALLUCINATION,

    UNSAFE_CONTENT,

    AGE_INAPPROPRIATE,

    OTHER;

    public static final Set<AiResponseReportReason> CHILD_SAFETY_CRITICAL =
            Collections.unmodifiableSet(EnumSet.of(UNSAFE_CONTENT, AGE_INAPPROPRIATE));

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
                        + "HALLUCINATION, UNSAFE_CONTENT, AGE_INAPPROPRIATE, or OTHER.");
    }

    public static AiResponseReportReason fromValueOrOther(String value) {
        return tryFrom(value).orElse(OTHER);
    }

    public boolean isChildSafetyCritical() {
        return CHILD_SAFETY_CRITICAL.contains(this);
    }
}
