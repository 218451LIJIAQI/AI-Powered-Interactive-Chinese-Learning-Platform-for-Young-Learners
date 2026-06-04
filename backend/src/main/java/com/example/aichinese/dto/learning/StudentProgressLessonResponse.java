package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "lessonId", "lessonTitle", "completionPercent", "completed", "lastAccessedAt" })
public record StudentProgressLessonResponse(
        Long lessonId,
        String lessonTitle,
        int completionPercent,
        boolean completed,
        LocalDateTime lastAccessedAt) {

    public StudentProgressLessonResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(lessonTitle, "lessonTitle must not be null");
        completionPercent = Math.max(0, Math.min(100, completionPercent));
    }
}
