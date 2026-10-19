package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "lessonId", "lessonTitle", "learningType" })
public record LiveClassLessonSummaryResponse(
        Long lessonId,
        String lessonTitle,
        String learningType) {

    public LiveClassLessonSummaryResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(lessonTitle, "lessonTitle must not be null");
    }
}
