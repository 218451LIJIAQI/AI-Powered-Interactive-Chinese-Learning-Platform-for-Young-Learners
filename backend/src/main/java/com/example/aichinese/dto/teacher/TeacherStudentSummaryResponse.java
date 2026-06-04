package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "studentId",
        "userId",
        "fullName",
        "email",
        "displayName",
        "currentLevel",
        "progressPercent",
        "quizAveragePercent",
        "atRisk",
        "weakAreas",
        "latestFeedbackType",
        "latestFeedbackAt",
        "latestActivityAt",
        "joinedAt"
})
public record TeacherStudentSummaryResponse(
        Long studentId,
        Long userId,
        String fullName,
        String email,
        String displayName,
        String currentLevel,
        Integer progressPercent,
        Double quizAveragePercent,
        boolean atRisk,
        List<String> weakAreas,
        String latestFeedbackType,
        LocalDateTime latestFeedbackAt,
        LocalDateTime latestActivityAt,
        LocalDateTime joinedAt) {

    public TeacherStudentSummaryResponse {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(progressPercent, "progressPercent must not be null");
        Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        weakAreas = weakAreas == null ? List.of() : List.copyOf(weakAreas);
    }
}
