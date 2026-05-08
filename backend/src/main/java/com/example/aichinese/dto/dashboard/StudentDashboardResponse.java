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
        List<RecommendedLessonSummary> recommendedLessons,
        List<SavedItemSummary> savedItems,
        List<NotificationSummary> recentNotifications,
        String aiRecommendedNextStep) {

    public record RecentQuizResult(
            Long attemptId,
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

    public record RecommendedLessonSummary(
            Long lessonId,
            String title,
            String category,
            String level) {
    }

    public record SavedItemSummary(
            Long savedItemId,
            String itemType,
            Long itemId,
            String note,
            LocalDateTime savedAt) {
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
