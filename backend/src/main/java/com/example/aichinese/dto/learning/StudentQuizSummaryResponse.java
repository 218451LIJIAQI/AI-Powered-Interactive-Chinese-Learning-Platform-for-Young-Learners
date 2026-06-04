package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "lessonId",
        "lessonTitle",
        "title",
        "description",
        "timeLimitMinutes",
        "totalPoints",
        "questionCount"
})
public record StudentQuizSummaryResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        Integer timeLimitMinutes,
        Integer totalPoints,
        int questionCount) {

    public StudentQuizSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        if (questionCount < 0) {
            throw new IllegalArgumentException("questionCount must be non-negative, was " + questionCount);
        }
    }
}
