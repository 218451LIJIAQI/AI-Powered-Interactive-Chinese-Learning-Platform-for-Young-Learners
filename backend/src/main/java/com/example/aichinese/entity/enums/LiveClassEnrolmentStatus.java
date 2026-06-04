package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum LiveClassEnrolmentStatus {

    ENROLLED,

    CANCELLED,

    ATTENDED,

    MISSED;

    public static final Set<LiveClassEnrolmentStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(CANCELLED, ATTENDED, MISSED));

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

    public boolean isActive() {
        return this == ENROLLED;
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isAttended() {
        return this == ATTENDED;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
