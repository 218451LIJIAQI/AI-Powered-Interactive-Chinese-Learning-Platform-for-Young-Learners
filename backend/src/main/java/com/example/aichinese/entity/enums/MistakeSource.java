package com.example.aichinese.entity.enums;

import java.util.Optional;

/** Where a waiting mistake was made: a Learn lesson or a Practice set. */
public enum MistakeSource {

    LEARN,

    PRACTICE;

    public static Optional<MistakeSource> tryFrom(String value) {
        return EnumLookups.tryFrom(MistakeSource.class, value);
    }
}
