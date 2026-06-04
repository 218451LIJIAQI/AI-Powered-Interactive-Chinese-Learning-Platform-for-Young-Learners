package com.example.aichinese.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "fullName",
        "totalStudents",
        "totalTeachers",
        "pendingTeacherApprovals",
        "totalModules",
        "totalLessons",
        "totalQuizzes",
        "totalGames",
        "recentUserActivity",
        "aiUsageSummary",
        "paymentRevenueSummary",
        "pendingRefunds",
        "pendingPayouts",
        "recentAuditLogs"
})
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
        List<RecentAuditLogSummary> recentAuditLogs) {

    public AdminDashboardResponse {
        Objects.requireNonNull(aiUsageSummary, "aiUsageSummary must not be null");
        Objects.requireNonNull(paymentRevenueSummary,
                "paymentRevenueSummary must not be null");
        recentUserActivity = copyRequired(recentUserActivity, "recentUserActivity");
        recentAuditLogs = copyRequired(recentAuditLogs, "recentAuditLogs");
    }

    private static <T> List<T> copyRequired(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(values, fieldName + " must not be null"));
    }

    @JsonPropertyOrder({ "userId", "fullName", "email", "roles", "createdAt" })
    public record RecentUserActivity(
            Long userId,
            String fullName,
            String email,
            List<String> roles,
            LocalDateTime createdAt) {

        public RecentUserActivity {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(email, "email must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
        }
    }

    @JsonPropertyOrder({ "totalRequests", "successfulRequests" })
    public record AiUsageSummary(
            long totalRequests,
            long successfulRequests) {
    }

    @JsonPropertyOrder({ "totalTransactions", "paidTransactions", "totalRevenue", "currencyCode" })
    public record PaymentRevenueSummary(
            long totalTransactions,
            long paidTransactions,
            BigDecimal totalRevenue,
            String currencyCode) {

        public PaymentRevenueSummary {
            if (totalRevenue == null) {
                totalRevenue = BigDecimal.ZERO;
            }
        }
    }

    @JsonPropertyOrder({
            "auditLogId",
            "actionType",
            "targetType",
            "description",
            "createdAt"
    })
    public record RecentAuditLogSummary(
            Long auditLogId,
            String actionType,
            String targetType,
            String description,
            LocalDateTime createdAt) {

        public RecentAuditLogSummary {
            Objects.requireNonNull(auditLogId, "auditLogId must not be null");
            Objects.requireNonNull(actionType, "actionType must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }
}
