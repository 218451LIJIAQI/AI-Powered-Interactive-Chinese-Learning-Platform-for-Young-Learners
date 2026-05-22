package com.example.aichinese.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

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

    public record RecentQuizResult(
            Long attemptId,
            Long quizId,
            String quizTitle,
            Integer score,
            Integer maxScore,
            LocalDateTime completedAt) {
    }

    public record LessonProgressSummary(
            Long lessonId,
            String lessonTitle,
            Integer completionPercent,
            LocalDateTime lastAccessedAt) {
    }

    public record NotificationSummary(
            Long notificationId,
            String title,
            String message,
            String status,
            String targetPath,
            LocalDateTime createdAt) {
    }
}
