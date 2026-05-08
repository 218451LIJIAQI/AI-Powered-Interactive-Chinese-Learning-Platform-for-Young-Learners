package com.example.aichinese.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        String fullName,
        long totalStudents,
        long totalTeachers,
        long pendingTeacherApprovals,
        long totalModules,
        long totalLessons,
        long totalQuizzes,
        long totalGames,
        List<RecentUserActivity> recentUserActivity,
        AiUsageSummary aiUsageSummary,
        PaymentRevenueSummary paymentRevenueSummary,
        long pendingRefunds,
        long pendingPayouts,
        List<RecentReportSummary> recentReports,
        List<RecentAuditLogSummary> recentAuditLogs) {

    public record RecentUserActivity(
            Long userId,
            String fullName,
            String email,
            List<String> roles,
            LocalDateTime createdAt) {
    }

    public record AiUsageSummary(
            long totalRequests,
            long successfulRequests) {
    }

    public record PaymentRevenueSummary(
            long totalTransactions,
            long paidTransactions,
            BigDecimal totalRevenue) {
    }

    public record RecentReportSummary(
            Long reportId,
            String targetType,
            String status,
            LocalDateTime createdAt) {
    }

    public record RecentAuditLogSummary(
            Long auditLogId,
            String actionType,
            String targetType,
            String description,
            LocalDateTime createdAt) {
    }
}
