package com.example.aichinese.entity.enums;

import java.util.Optional;

/**
 * The six Practice activity types, in the order of the Practice tabs. URL slugs are the lower-case names with
 * hyphens ({@code drag-drop}).
 */
public enum PracticeType {

    /** Cards rated "Got it" or "Still learning"; a card still being learned becomes a mistake. */
    FLASHCARDS,

    LISTENING,

    TRACING,

    DRAG_DROP,

    MATCHING,

    /** Pinyin typing: read the meaning, type the pinyin, choose the right characters among the words it can spell. */
    TYPING;

    /** Stored type for XP sources and for audit and notification targets that identify a practice set. */
    public String referenceType() {
        return "PRACTICE_" + name();
    }

    /** The types whose answers are submitted with how sure the student is (Guessing or Sure). */
    public boolean asksConfidence() {
        return this == LISTENING || this == DRAG_DROP || this == TYPING;
    }

    /** Case-insensitive; accepts {@code drag-drop} as well as {@code DRAG_DROP}. */
    public static Optional<PracticeType> tryFrom(String value) {
        return EnumLookups.tryFrom(PracticeType.class, value == null ? null : value.replace('-', '_'));
    }
}
