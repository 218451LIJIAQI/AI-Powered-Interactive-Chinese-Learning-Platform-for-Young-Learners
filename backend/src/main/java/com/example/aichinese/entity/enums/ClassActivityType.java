package com.example.aichinese.entity.enums;

import java.util.Optional;

/** The three practice activities a class lesson can hold, each stored in its own activity table. */
public enum ClassActivityType {

    FLASHCARDS,

    TRACING,

    MATCHING;

    public static Optional<ClassActivityType> tryFrom(String value) {
        return EnumLookups.tryFrom(ClassActivityType.class, value);
    }
}
