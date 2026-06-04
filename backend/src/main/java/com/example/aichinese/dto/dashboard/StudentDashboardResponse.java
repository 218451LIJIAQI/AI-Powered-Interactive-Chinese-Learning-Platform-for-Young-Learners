package com.example.aichinese.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "fullName",
        "currentLevel",
        "learningProgressPercent",
        "dailyStreak",
        "points",
        "badgeCount",
        "recentQuizResults",
        "weakAreas",
        "unfinishedLessons",
        "recentNotifications",
        "recommendedNextStep"
})
public record StudentDashboardResponse(
        String fullName,
        String currentLevel,
        int learningProgressPercent,
        int dailyStreak,
        int points,
        long badgeCount,
        List<RecentQuizResult> recentQuizResults,
        List<String> weakAreas,
        List<LessonProgressSummary> unfinishedLessons,
        List<NotificationSummary> recentNotifications,
        String recommendedNextStep) {

    public StudentDashboardResponse {
        recentQuizResults = copyRequired(recentQuizResults, "recentQuizResults");
        weakAreas = copyRequired(weakAreas, "weakAreas");
        unfinishedLessons = copyRequired(unfinishedLessons, "unfinishedLessons");
        recentNotifications = copyRequired(recentNotifications, "recentNotifications");
    }

    private static <T> List<T> copyRequired(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(values, fieldName + " must not be null"));
    }

    @JsonPropertyOrder({
            "attemptId",
            "quizId",
            "quizTitle",
            "score",
            "maxScore",
            "completedAt"
    })
    public record RecentQuizResult(
            Long attemptId,
            Long quizId,
            String quizTitle,
            Integer score,
            Integer maxScore,
            LocalDateTime completedAt) {

        public RecentQuizResult {
            Objects.requireNonNull(attemptId, "attemptId must not be null");
        }
    }

    @JsonPropertyOrder({
            "lessonId",
            "lessonTitle",
            "completionPercent",
            "lastAccessedAt"
    })
    public record LessonProgressSummary(
            Long lessonId,
            String lessonTitle,
            Integer completionPercent,
            LocalDateTime lastAccessedAt) {

        public LessonProgressSummary {
            Objects.requireNonNull(lessonId, "lessonId must not be null");
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
