package com.example.aichinese.dto.analytics;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "overview",
        "userRegistrationTrend",
        "teacherApprovalBreakdown",
        "contentStatusBreakdown",
        "aiStatusBreakdown"
})
public record AdminAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> userRegistrationTrend,
        List<AnalyticsBreakdownItemResponse> teacherApprovalBreakdown,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> aiStatusBreakdown) {

    public AdminAnalyticsResponse {
        Objects.requireNonNull(overview, "overview must not be null");
        userRegistrationTrend = copyRequired(userRegistrationTrend, "userRegistrationTrend");
        teacherApprovalBreakdown = copyRequired(teacherApprovalBreakdown, "teacherApprovalBreakdown");
        contentStatusBreakdown = copyRequired(contentStatusBreakdown, "contentStatusBreakdown");
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
            long moderationActions,
            long auditLogEntries,
            long adminUnreadNotifications,
            long aiRequests) {
    }
}
