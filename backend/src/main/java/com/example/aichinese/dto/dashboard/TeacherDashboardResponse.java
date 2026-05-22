package com.example.aichinese.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

public record TeacherDashboardResponse(
        String fullName,
        TeacherStatus teacherStatus,
        long totalLessons,
        long totalStudents,
        BigDecimal averageQuizScore,
        EarningsSummary earningsSummary,
        List<RecentQuizResult> recentQuizResults,
        List<NotificationSummary> recentNotifications) {

    public record EarningsSummary(
            BigDecimal grossAmount,
            BigDecimal netAmount,
            BigDecimal availableBalance,
            BigDecimal pendingBalance) {
    }

    public record RecentQuizResult(
            Long attemptId,
            String quizTitle,
            String studentName,
            Integer score,
            Integer maxScore,
            LocalDateTime completedAt) {
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
