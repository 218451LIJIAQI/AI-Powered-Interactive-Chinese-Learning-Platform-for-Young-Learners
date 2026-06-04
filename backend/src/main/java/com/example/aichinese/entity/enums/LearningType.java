package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum LearningType {

    LIVE,

    RECORDED,

    HYBRID;

    private static final Map<String, LearningType> LOOKUP = EnumLookups.indexByName(LearningType.class);

    public static Optional<LearningType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static LearningType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Learning type is required.",
                () -> "Invalid learning type. Allowed values are LIVE, RECORDED, or HYBRID.");
    }

    public boolean hasLiveComponent() {
        return this == LIVE || this == HYBRID;
    }

    public boolean hasRecordedComponent() {
        return this == RECORDED || this == HYBRID;
    }
}
