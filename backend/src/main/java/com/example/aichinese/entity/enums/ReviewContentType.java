package com.example.aichinese.entity.enums;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Every kind of content that goes through the teacher-submit / admin-review workflow (D4, D5).
 * {@link #PACKAGE} is a free teacher-curated "Course" (stored as a CoursePackage).
 */
public enum ReviewContentType {

    PACKAGE("Course"),

    MODULE("Module"),

    LESSON("Lesson"),

    QUIZ("Quiz"),

    FLASHCARD("Flashcard"),

    MATCHING_GAME("Matching game"),

    LISTENING_EXERCISE("Listening exercise"),

    TRACING_EXERCISE("Tracing exercise"),

    DRAG_DROP_ACTIVITY("Drag-and-drop activity"),

    STORY_ACTIVITY("Story activity");

    private static final Map<String, ReviewContentType> LOOKUP =
            EnumLookups.indexByName(ReviewContentType.class);

    private final String label;

    ReviewContentType(String label) {
        this.label = label;
    }

    /** Human-readable name used in messages, notifications and audit descriptions. */
    public String label() {
        return label;
    }

    /** Case-insensitive; accepts {@code drag-drop-activity} as well as {@code DRAG_DROP_ACTIVITY}. */
    public static Optional<ReviewContentType> tryFrom(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return EnumLookups.tryFrom(LOOKUP, value.replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public static ReviewContentType fromValue(String value) {
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid content type. Allowed values are PACKAGE, MODULE, LESSON, QUIZ, FLASHCARD, "
                        + "MATCHING_GAME, LISTENING_EXERCISE, TRACING_EXERCISE, DRAG_DROP_ACTIVITY, or STORY_ACTIVITY."));
    }
}
