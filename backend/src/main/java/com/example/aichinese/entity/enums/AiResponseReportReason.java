package com.example.aichinese.entity.enums;

import java.util.Optional;

public enum AiResponseReportReason {

    INCORRECT_CONTENT,

    UNCLEAR_EXPLANATION,

    /** Unsafe or inappropriate for the platform. */
    INAPPROPRIATE_CONTENT,

    /** A pronunciation or handwriting practice score the student disagrees with. */
    SCORE_SEEMS_WRONG,

    OTHER;

    public static Optional<AiResponseReportReason> tryFrom(String value) {
        return EnumLookups.tryFrom(AiResponseReportReason.class, value);
    }
}
