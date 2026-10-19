package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Teacher review state of a practice submission (D7a): SUBMITTED until an approved teacher writes feedback.
 */
public enum PracticeReviewStatus {

    SUBMITTED,

    REVIEWED;

    private static final Map<String, PracticeReviewStatus> LOOKUP =
            EnumLookups.indexByName(PracticeReviewStatus.class);

    public static Optional<PracticeReviewStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static PracticeReviewStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Review status is required.",
                () -> "Invalid value. Allowed values are SUBMITTED, REVIEWED.");
    }
}
