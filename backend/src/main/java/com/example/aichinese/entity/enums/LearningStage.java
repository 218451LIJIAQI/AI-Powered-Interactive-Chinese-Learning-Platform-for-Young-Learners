package com.example.aichinese.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Optional scaffolding stage metadata on practice and lesson activities (Proposal 5.3, ZPD; FRCONT10).
 * Used by {@code TracingPlayer} to hide the faint model glyph until a hint is opened for {@link #INDEPENDENT} sets;
 * hint availability and depth are governed by {@code HintPolicyService} and {@code Quiz.hintsAllowed}.
 */
public enum LearningStage {

    GUIDED,

    SCAFFOLDED,

    INDEPENDENT;

    /** Lenient JSON binding: case-insensitive, blank means "no stage". */
    @JsonCreator
    public static LearningStage fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return EnumLookups.tryFrom(LearningStage.class, value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid learning stage. Allowed values are GUIDED, SCAFFOLDED, or INDEPENDENT."));
    }
}
