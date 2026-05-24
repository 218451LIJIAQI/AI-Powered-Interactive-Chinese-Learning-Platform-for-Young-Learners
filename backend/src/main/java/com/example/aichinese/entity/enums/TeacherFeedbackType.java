package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Pedagogical category of a teacher's free-text feedback to a student.
 *
 * <p>The category drives notification icons, analytics aggregation and
 * the AI assistant's downstream summarisation. Persisted with
 * {@code @Enumerated(EnumType.STRING)} on {@code TeacherFeedback}. The
 * constant names are part of the database contract — they MUST NOT be
 * renamed or removed without a paired Flyway migration.</p>
 */
public enum TeacherFeedbackType {

    /** Untyped general comment that does not fit any other category. */
    GENERAL,

    /** Feedback tied to a specific quiz attempt or its individual answers. */
    QUIZ,

    /** Feedback tied to a specific lesson (live or recorded). */
    LESSON,

    /** Positive reinforcement intended to motivate the learner. */
    ENCOURAGING,

    /** Constructive correction pointing out a specific mistake or misconception. */
    CORRECTIVE,

    /** Periodic progress summary covering recent activity. */
    PROGRESS,

    /** Targeted note about a recurring weak area that needs more practice. */
    WEAK_AREA;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, TeacherFeedbackType> LOOKUP =
            EnumLookups.indexByName(TeacherFeedbackType.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised feedback type
     */
    public static Optional<TeacherFeedbackType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised feedback type
     */
    public static TeacherFeedbackType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Teacher feedback type is required.",
                () -> "Invalid teacher feedback type. Allowed values are GENERAL, QUIZ, LESSON, "
                        + "ENCOURAGING, CORRECTIVE, PROGRESS, or WEAK_AREA.");
    }

    /**
     * Lenient counterpart that falls back to {@link #GENERAL} when the input
     * is missing or unrecognised. Suitable for legacy import paths where
     * absent feedback types should not break the request.
     */
    public static TeacherFeedbackType fromValueOrGeneral(String value) {
        return tryFrom(value).orElse(GENERAL);
    }

    /** @return {@code true} when this category positively reinforces behaviour. */
    public boolean isPositive() {
        return this == ENCOURAGING || this == PROGRESS;
    }

    /** @return {@code true} when this category points out something to improve. */
    public boolean isCorrective() {
        return this == CORRECTIVE || this == WEAK_AREA;
    }
}
