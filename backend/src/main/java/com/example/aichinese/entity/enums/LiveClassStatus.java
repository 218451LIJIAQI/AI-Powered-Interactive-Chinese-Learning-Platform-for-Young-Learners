package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum LiveClassStatus {

    SCHEDULED,

    ONGOING,

    COMPLETED,

    CANCELLED,

    RESCHEDULED;

    public static final Set<LiveClassStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(COMPLETED, CANCELLED));
}
