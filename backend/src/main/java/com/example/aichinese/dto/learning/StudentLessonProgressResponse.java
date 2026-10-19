package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Lesson progress after the student viewed the lesson content (D25). */
@JsonPropertyOrder({ "lessonId", "completionPercent", "completed", "mastery", "bestQuizPercentage", "contentViewed" })
public record StudentLessonProgressResponse(
        Long lessonId,
        int completionPercent,
        boolean completed,
        String mastery,
        Integer bestQuizPercentage,
        boolean contentViewed) {

    public StudentLessonProgressResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(mastery, "mastery must not be null");
        completionPercent = Math.max(0, Math.min(100, completionPercent));
    }
}
