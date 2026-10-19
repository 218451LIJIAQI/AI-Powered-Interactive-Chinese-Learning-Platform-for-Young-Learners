package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum ContentModerationTargetType {

    LESSON,

    /** A learning module; archiving it hides its lessons from students (D14). */
    MODULE,

    /** A free, teacher-curated course (stored as a CoursePackage). */
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
                () -> "Invalid moderation target type. Allowed values are LESSON, MODULE, PACKAGE, "
                        + "TEACHER_PROFILE, or OTHER.");
    }
}
