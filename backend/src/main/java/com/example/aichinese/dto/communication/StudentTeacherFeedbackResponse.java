package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "teacherName",
        "lessonId",
        "lessonTitle",
        "quizAttemptId",
        "quizTitle",
        "practiceSubmissionId",
        "feedbackType",
        "message",
        "createdAt",
        "updatedAt"
})
public record StudentTeacherFeedbackResponse(
        Long id,
        String teacherName,
        Long lessonId,
        String lessonTitle,
        Long quizAttemptId,
        String quizTitle,
        Long practiceSubmissionId,
        String feedbackType,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public StudentTeacherFeedbackResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
