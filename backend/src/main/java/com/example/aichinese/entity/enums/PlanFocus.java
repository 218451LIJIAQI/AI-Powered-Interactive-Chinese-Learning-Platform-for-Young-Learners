package com.example.aichinese.entity.enums;

import java.util.List;

/**
 * What a student wants to get better at (setup step 5, up to two). The plan adds practice for it: each focus maps to
 * a Practice type, or to Talk with Xiaomei for speaking. The keywords match the focus to classes by their titles,
 * descriptions and themes, as {@link PlanGoal}'s do for goals.
 */
public enum PlanFocus {

    SPEAKING(null, List.of("speaking", "pronunciation", "tone")),

    LISTENING(PracticeType.LISTENING, List.of("listening")),

    READING(PracticeType.FLASHCARDS, List.of("reading", "character", "pinyin")),

    WRITING(PracticeType.TRACING, List.of("writing", "character", "stroke"));

    private final PracticeType practiceType;
    private final List<String> keywords;

    PlanFocus(PracticeType practiceType, List<String> keywords) {
        this.practiceType = practiceType;
        this.keywords = keywords;
    }

    /** The Practice type that trains this focus; null when Talk with Xiaomei does (speaking). */
    public PracticeType practiceType() {
        return practiceType;
    }

    public List<String> keywords() {
        return keywords;
    }
}
