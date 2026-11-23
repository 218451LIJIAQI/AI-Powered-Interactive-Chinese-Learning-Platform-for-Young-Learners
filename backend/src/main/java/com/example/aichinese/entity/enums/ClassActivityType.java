package com.example.aichinese.entity.enums;

import java.util.Optional;

/** The three practice activities a class lesson can hold, each stored in its own activity table. */
public enum ClassActivityType {

    FLASHCARDS(1),

    TRACING(2),

    MATCHING(3);

    private final int lessonPosition;

    ClassActivityType(int lessonPosition) {
        this.lessonPosition = lessonPosition;
    }

    /** The fixed slot shown in the lesson editor and followed by learners. */
    public int lessonPosition() {
        return lessonPosition;
    }

    public static Optional<ClassActivityType> tryFrom(String value) {
        return EnumLookups.tryFrom(ClassActivityType.class, value);
    }
}
