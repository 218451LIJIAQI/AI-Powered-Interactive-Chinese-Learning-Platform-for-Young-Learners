package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum ContentModerationTargetType {

    LESSON,

    PACKAGE,

    TEACHER_PROFILE,

    OTHER;

    private static final Map<String, ContentModerationTargetType> LOOKUP =
            EnumLookups.indexByName(ContentModerationTargetType.class);

    public static Optional<ContentModerationTargetType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static ContentModerationTargetType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Moderation target type is required.",
                () -> "Invalid moderation target type. Allowed values are LESSON, PACKAGE, "
                        + "TEACHER_PROFILE, or OTHER.");
    }

    public static ContentModerationTargetType fromValueOrOther(String value) {
        return tryFrom(value).orElse(OTHER);
    }
}
