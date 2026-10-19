package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Kind of learner practice upload kept for teacher review (D7a).
 */
public enum PracticeSubmissionType {

    PRONUNCIATION_AUDIO,

    HANDWRITING_IMAGE;

    private static final Map<String, PracticeSubmissionType> LOOKUP =
            EnumLookups.indexByName(PracticeSubmissionType.class);

    public static Optional<PracticeSubmissionType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static PracticeSubmissionType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Submission type is required.",
                () -> "Invalid value. Allowed values are PRONUNCIATION_AUDIO, HANDWRITING_IMAGE.");
    }
}
