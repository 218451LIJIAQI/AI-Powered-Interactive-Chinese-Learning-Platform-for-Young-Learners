package com.example.aichinese.entity.enums;

public enum AiResponseReportReason {

    INCORRECT_CONTENT,

    UNCLEAR_EXPLANATION,

    /** Unsafe or inappropriate for the platform. */
    INAPPROPRIATE_CONTENT,

    /** A pronunciation or handwriting practice score the student disagrees with. */
    SCORE_SEEMS_WRONG,

    /** An AI-written plan text that does not match what the learner wrote in the setup (X2.23). */
    DOES_NOT_MATCH_INPUT,

    OTHER
}
