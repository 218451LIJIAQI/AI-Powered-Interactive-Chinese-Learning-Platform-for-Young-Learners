package com.example.aichinese.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Optional scaffolding stage of a quiz or practice activity (Proposal 5.3, ZPD; FRCONT10); students see its label. */
public enum LearningStage {

    GUIDED,

    SCAFFOLDED,

    INDEPENDENT;

    public static LearningStage fromValue(String value) {
        return EnumLookups.fromValue(
                LearningStage.class,
                value,
                "Learning stage is required.",
                () -> "Invalid learning stage. Allowed values are GUIDED, SCAFFOLDED, or INDEPENDENT.");
    }

    /** Lenient JSON binding: case-insensitive, blank means "no stage". */
    @JsonCreator
    public static LearningStage fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return fromValue(value);
    }
}
