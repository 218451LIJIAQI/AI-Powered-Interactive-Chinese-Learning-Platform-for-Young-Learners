package com.example.aichinese.entity.enums;

import java.util.Optional;

/** What a student sends SparkBot to score: a speaking recording or a handwriting photo. */
public enum PracticeCheckType {

    RECORDING,

    HANDWRITING;

    public static Optional<PracticeCheckType> tryFrom(String value) {
        return EnumLookups.tryFrom(PracticeCheckType.class, value);
    }
}
