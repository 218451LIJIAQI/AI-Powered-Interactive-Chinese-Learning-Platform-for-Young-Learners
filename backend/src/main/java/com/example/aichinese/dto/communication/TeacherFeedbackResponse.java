package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "teacherId",
        "teacherName",
        "studentId",
        "studentName",
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
public record TeacherFeedbackResponse(
        Long id,
        Long teacherId,
        String teacherName,
        Long studentId,
        String studentName,
        Long lessonId,
        String lessonTitle,
        Long quizAttemptId,
        String quizTitle,
        Long practiceSubmissionId,
        String feedbackType,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public TeacherFeedbackResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
