package com.example.aichinese.entity.enums;

import java.util.Optional;

/** The Mandarin level names of the platform: New to Mandarin, then Beginner 1 (HSK 1), then Beginner 2 (HSK 2). */
public enum MandarinLevel {

    NEW_TO_MANDARIN,

    BEGINNER_1,

    BEGINNER_2;

    public static Optional<MandarinLevel> tryFrom(String value) {
        return EnumLookups.tryFrom(MandarinLevel.class, value);
    }
}
