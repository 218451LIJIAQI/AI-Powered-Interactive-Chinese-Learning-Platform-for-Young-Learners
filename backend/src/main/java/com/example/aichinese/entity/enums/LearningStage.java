package com.example.aichinese.entity.enums;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Optional scaffolding stage of a quiz or practice activity (Proposal 5.3, ZPD; decision D23).
 * Students see the stage label and lesson practice is ordered guided, then scaffolded, then
 * independent; items without a stage come last.
 */
public enum LearningStage {

    GUIDED,

    SCAFFOLDED,

    INDEPENDENT;

    /** Guided first, then scaffolded, then independent, then items without a stage. */
    public static final Comparator<LearningStage> STUDENT_ORDER =
            Comparator.nullsLast(Comparator.comparingInt(LearningStage::ordinal));

    private static final Map<String, LearningStage> LOOKUP =
            EnumLookups.indexByName(LearningStage.class);

    public static Optional<LearningStage> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static LearningStage fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
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
