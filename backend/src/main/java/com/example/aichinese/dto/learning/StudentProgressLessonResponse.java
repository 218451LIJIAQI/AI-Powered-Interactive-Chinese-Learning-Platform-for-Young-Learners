package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Per-lesson progress row. {@code mastery} is NOT_STARTED, IN_PROGRESS, COMPLETED or MASTERED (D25);
 * {@code bestQuizPercentage} is null for lessons without attempted published quizzes.
 */
@JsonPropertyOrder({
        "lessonId",
        "lessonTitle",
        "completionPercent",
        "completed",
        "lastAccessedAt",
        "mastery",
        "bestQuizPercentage"
})
public record StudentProgressLessonResponse(
        Long lessonId,
        String lessonTitle,
        int completionPercent,
        boolean completed,
        LocalDateTime lastAccessedAt,
        String mastery,
        Integer bestQuizPercentage) {

    public StudentProgressLessonResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(lessonTitle, "lessonTitle must not be null");
        completionPercent = Math.max(0, Math.min(100, completionPercent));
        mastery = mastery == null ? "NOT_STARTED" : mastery;
    }
}
