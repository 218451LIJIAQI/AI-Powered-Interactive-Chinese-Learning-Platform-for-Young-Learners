package com.example.aichinese.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({
        "fullName",
        "teacherStatus",
        "totalLessons",
        "totalStudents",
        "averageQuizScore",
        "recentQuizResults",
        "recentNotifications"
})
public record TeacherDashboardResponse(
        String fullName,
        TeacherStatus teacherStatus,
        long totalLessons,
        long totalStudents,
        BigDecimal averageQuizScore,
        List<RecentQuizResult> recentQuizResults,
        List<NotificationSummary> recentNotifications) {

    public TeacherDashboardResponse {
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
        if (averageQuizScore == null) {
            averageQuizScore = BigDecimal.ZERO;
        }
        recentQuizResults = copyRequired(recentQuizResults, "recentQuizResults");
        recentNotifications = copyRequired(recentNotifications, "recentNotifications");
    }

    private static <T> List<T> copyRequired(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(values, fieldName + " must not be null"));
    }

    @JsonPropertyOrder({
            "attemptId",
            "quizTitle",
            "studentName",
            "score",
            "maxScore",
            "completedAt"
    })
    public record RecentQuizResult(
            Long attemptId,
            String quizTitle,
            String studentName,
            Integer score,
            Integer maxScore,
            LocalDateTime completedAt) {

        public RecentQuizResult {
            Objects.requireNonNull(attemptId, "attemptId must not be null");
        }
    }

    @JsonPropertyOrder({
            "notificationId",
            "title",
            "message",
            "status",
            "targetPath",
            "createdAt"
    })
    public record NotificationSummary(
            Long notificationId,
            String title,
            String message,
            String status,
            String targetPath,
            LocalDateTime createdAt) {

        public NotificationSummary {
            Objects.requireNonNull(notificationId, "notificationId must not be null");
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(message, "message must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }
}
