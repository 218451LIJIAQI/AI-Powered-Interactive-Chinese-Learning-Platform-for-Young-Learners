package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "lessonId", "lessonTitle", "learningType", "accessType" })
public record LiveClassLessonSummaryResponse(
        Long lessonId,
        String lessonTitle,
        String learningType,
        String accessType) {

    public LiveClassLessonSummaryResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(lessonTitle, "lessonTitle must not be null");
    }
}
