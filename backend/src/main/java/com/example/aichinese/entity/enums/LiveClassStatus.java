package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum LiveClassStatus {

    SCHEDULED,

    ONGOING,

    COMPLETED,

    CANCELLED,

    RESCHEDULED;

    public static final Set<LiveClassStatus> JOINABLE =
            Collections.unmodifiableSet(EnumSet.of(SCHEDULED, ONGOING));

    public static final Set<LiveClassStatus> STUDENT_VISIBLE =
            Collections.unmodifiableSet(EnumSet.of(SCHEDULED, ONGOING, RESCHEDULED));

    public static final Set<LiveClassStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(COMPLETED, CANCELLED));

    private static final Map<String, LiveClassStatus> LOOKUP =
            EnumLookups.indexByName(LiveClassStatus.class);

    public static Optional<LiveClassStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static LiveClassStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Live class status is required.",
                () -> "Invalid live class status. Allowed values are SCHEDULED, ONGOING, "
                        + "COMPLETED, CANCELLED, or RESCHEDULED.");
    }

    public boolean isStudentVisible() {
        return STUDENT_VISIBLE.contains(this);
    }
}
