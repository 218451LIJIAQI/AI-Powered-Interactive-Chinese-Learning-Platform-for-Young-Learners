package com.example.aichinese.entity.enums;

import java.util.Optional;

/**
 * The seven Practice activity types, in the order of the Practice tabs. URL slugs are the lower-case names with
 * hyphens ({@code drag-drop}).
 */
public enum PracticeType {

    FLASHCARDS,

    QUIZZES,

    LISTENING,

    TRACING,

    DRAG_DROP,

    MATCHING,

    STORY;

    /** Case-insensitive; accepts {@code drag-drop} as well as {@code DRAG_DROP}. */
    public static Optional<PracticeType> tryFrom(String value) {
        return EnumLookups.tryFrom(PracticeType.class, value == null ? null : value.replace('-', '_'));
    }
}
