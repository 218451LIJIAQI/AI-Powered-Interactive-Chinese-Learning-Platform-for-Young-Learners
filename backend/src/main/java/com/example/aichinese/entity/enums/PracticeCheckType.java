package com.example.aichinese.entity.enums;

import java.util.Optional;

/** A pronunciation or handwriting practice check: a speaking recording or a handwriting photo that SparkBot scores. */
public enum PracticeCheckType {

    RECORDING,

    HANDWRITING,

    /** Legacy database value. It is excluded from every learner-facing practice-check operation. */
    TONES;

    public static Optional<PracticeCheckType> tryFrom(String value) {
        return EnumLookups.tryFrom(PracticeCheckType.class, value);
    }
}
