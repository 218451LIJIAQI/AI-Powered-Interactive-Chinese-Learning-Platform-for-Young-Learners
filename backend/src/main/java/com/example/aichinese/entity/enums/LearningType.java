package com.example.aichinese.entity.enums;

import java.util.Optional;

public enum LearningType {

    LIVE,

    RECORDED,

    HYBRID;

    public static Optional<LearningType> tryFrom(String value) {
        return EnumLookups.tryFrom(LearningType.class, value);
    }
}
