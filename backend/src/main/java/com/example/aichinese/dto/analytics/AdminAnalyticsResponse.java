package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "overview",
        "userRegistrationTrend",
        "teacherApprovalBreakdown",
        "contentStatusBreakdown",
        "paymentStatusBreakdown",
        "aiStatusBreakdown"
})
public record AdminAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> userRegistrationTrend,
        List<AnalyticsBreakdownItemResponse> teacherApprovalBreakdown,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> paymentStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> aiStatusBreakdown) {

    public AdminAnalyticsResponse {
        Objects.requireNonNull(overview, "overview must not be null");
        userRegistrationTrend = copyRequired(userRegistrationTrend, "userRegistrationTrend");
        teacherApprovalBreakdown = copyRequired(teacherApprovalBreakdown, "teacherApprovalBreakdown");
        contentStatusBreakdown = copyRequired(contentStatusBreakdown, "contentStatusBreakdown");
        paymentStatusBreakdown = copyRequired(paymentStatusBreakdown, "paymentStatusBreakdown");
        aiStatusBreakdown = copyRequired(aiStatusBreakdown, "aiStatusBreakdown");
    }

    private static <T> List<T> copyRequired(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(values, fieldName + " must not be null"));
    }

    @JsonPropertyOrder({
            "totalStudents",
            "totalTeachers",
            "pendingTeacherApprovals",
            "approvedTeachers",
            "rejectedTeachers",
            "publishedContentItems",
            "totalRevenue",
            "currencyCode",
            "pendingRefunds",
            "pendingPayouts",
            "moderationActions",
            "auditLogEntries",
            "adminUnreadNotifications",
            "aiRequests"
    })
    public record Overview(
            long totalStudents,
            long totalTeachers,
            long pendingTeacherApprovals,
            long approvedTeachers,
            long rejectedTeachers,
            long publishedContentItems,
            BigDecimal totalRevenue,
            String currencyCode,
            long pendingRefunds,
            long pendingPayouts,
            long moderationActions,
            long auditLogEntries,
            long adminUnreadNotifications,
            long aiRequests) {

        public Overview {
            if (totalRevenue == null) {
                totalRevenue = BigDecimal.ZERO;
            }
        }
    }
}
