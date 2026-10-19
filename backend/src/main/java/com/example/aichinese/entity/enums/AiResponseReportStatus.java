package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum AiResponseReportStatus {

    PENDING_REVIEW,

    IN_REVIEW,

    RESOLVED,

    DISMISSED;

    public static final Set<AiResponseReportStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(RESOLVED, DISMISSED));

    private static final Map<String, AiResponseReportStatus> LOOKUP =
            EnumLookups.indexByName(AiResponseReportStatus.class);

    public static Optional<AiResponseReportStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static AiResponseReportStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI report status is required.",
                () -> "Invalid AI report status. Allowed values are PENDING_REVIEW, IN_REVIEW, "
                        + "RESOLVED, or DISMISSED.");
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
