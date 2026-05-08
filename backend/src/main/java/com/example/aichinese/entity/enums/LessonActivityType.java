package com.example.aichinese.entity.enums;

import java.util.Arrays;

public enum LessonActivityType {
    FLASHCARD,
    QUIZ,
    MATCHING,
    LISTENING,
    TRACING,
    DRAG_DROP,
    STORY,
    AI_HELP,
    TEXT_CONTENT,
    VIDEO_PLACEHOLDER;

    public static LessonActivityType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Activity type is required.");
        }

        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid activity type. Allowed values are FLASHCARD, QUIZ, MATCHING, LISTENING, TRACING, DRAG_DROP, STORY, AI_HELP, TEXT_CONTENT, or VIDEO_PLACEHOLDER."));
    }
}
