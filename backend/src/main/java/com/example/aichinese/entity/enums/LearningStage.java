package com.example.aichinese.entity.enums;

/**
 * Optional scaffolding stage metadata on practice and lesson activities (Proposal 5.3, ZPD; FRCONT10).
 * Used by {@code TracingPlayer} to hide the faint model glyph until a hint is opened for {@link #INDEPENDENT} sets;
 * hint availability and depth are governed by {@code HintPolicyService} and {@code Quiz.hintsAllowed}.
 */
public enum LearningStage {

    GUIDED,

    SCAFFOLDED,

    INDEPENDENT
}
