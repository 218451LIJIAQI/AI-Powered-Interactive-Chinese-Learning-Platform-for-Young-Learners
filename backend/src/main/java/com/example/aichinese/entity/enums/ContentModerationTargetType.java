package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Kind of artefact a {@code ContentModerationRecord} is acting upon.
 *
 * <p>Used by the admin moderation tooling to decide which navigation
 * target to link from a moderation entry, and to scope policy enforcement
 * routines to the correct entity type. Persisted with
 * {@code @Enumerated(EnumType.STRING)}. The constant names are part of
 * the database contract — they MUST NOT be renamed or removed without a
 * paired Flyway migration.</p>
 */
public enum ContentModerationTargetType {

    /** Single {@code Lesson} entity (live or recorded). */
    LESSON,

    /** Bundled {@code CoursePackage} that aggregates lessons or modules. */
    PACKAGE,

    /** Public-facing {@code TeacherProfile} card and biography. */
    TEACHER_PROFILE,

    /** Catch-all for moderation actions that do not fit the above. */
    OTHER;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, ContentModerationTargetType> LOOKUP =
            EnumLookups.indexByName(ContentModerationTargetType.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised target type
     */
    public static Optional<ContentModerationTargetType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised target type
     */
    public static ContentModerationTargetType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Moderation target type is required.",
                () -> "Invalid moderation target type. Allowed values are LESSON, PACKAGE, "
                        + "TEACHER_PROFILE, or OTHER.");
    }

    /**
     * Lenient counterpart that falls back to {@link #OTHER} for missing or
     * unrecognised input.
     */
    public static ContentModerationTargetType fromValueOrOther(String value) {
        return tryFrom(value).orElse(OTHER);
    }
}
