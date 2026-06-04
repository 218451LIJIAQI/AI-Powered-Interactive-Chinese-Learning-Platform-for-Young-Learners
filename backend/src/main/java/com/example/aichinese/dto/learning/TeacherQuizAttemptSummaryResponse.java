package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "attemptId", "studentId", "studentName", "score", "maxScore", "submittedAt" })
public record TeacherQuizAttemptSummaryResponse(
        Long attemptId,
        Long studentId,
        String studentName,
        Integer score,
        Integer maxScore,
        LocalDateTime submittedAt) {

    public TeacherQuizAttemptSummaryResponse {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(studentName, "studentName must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }
}
