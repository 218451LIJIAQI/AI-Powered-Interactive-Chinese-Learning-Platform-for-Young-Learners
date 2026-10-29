package com.example.aichinese.entity.enums;

/**
 * What a student wants to get better at (setup step 5, up to two). The plan adds practice for it: each focus maps to
 * a Practice type, or to Talk with Xiaomei for speaking and conversation.
 */
public enum PlanFocus {

    SPEAKING(null),

    LISTENING(PracticeType.LISTENING),

    READING(PracticeType.FLASHCARDS),

    WRITING(PracticeType.TRACING),

    VOCABULARY(PracticeType.MATCHING),

    CONVERSATION(null),

    BALANCED(PracticeType.QUIZZES);

    private final PracticeType practiceType;

    PlanFocus(PracticeType practiceType) {
        this.practiceType = practiceType;
    }

    /** The Practice type that trains this focus; null when Talk with Xiaomei does (speaking, conversation). */
    public PracticeType practiceType() {
        return practiceType;
    }
}
