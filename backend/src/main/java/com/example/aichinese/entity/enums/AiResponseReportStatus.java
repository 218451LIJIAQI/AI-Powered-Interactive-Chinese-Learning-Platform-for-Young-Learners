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

    public static final Set<AiResponseReportStatus> OPEN =
            Collections.unmodifiableSet(EnumSet.of(PENDING_REVIEW, IN_REVIEW));

    public static final Set<AiResponseReportStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(RESOLVED, DISMISSED));

    static {
        EnumSet<AiResponseReportStatus> covered = EnumSet.copyOf(OPEN);
        covered.addAll(TERMINAL);
        if (!covered.equals(EnumSet.allOf(AiResponseReportStatus.class))) {
            EnumSet<AiResponseReportStatus> missing = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "AiResponseReportStatus category sets do not cover all constants; uncovered: " + missing);
        }
        EnumSet<AiResponseReportStatus> overlap = EnumSet.copyOf(OPEN);
        overlap.retainAll(TERMINAL);
        if (!overlap.isEmpty()) {
            throw new AssertionError(
                    "AiResponseReportStatus OPEN and TERMINAL sets must be disjoint; overlap: " + overlap);
        }
    }

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

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isResolved() {
        return this == RESOLVED;
    }
}
