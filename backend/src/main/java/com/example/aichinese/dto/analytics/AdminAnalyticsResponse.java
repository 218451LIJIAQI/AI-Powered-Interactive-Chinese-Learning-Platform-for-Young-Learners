package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public record AdminAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> userRegistrationTrend,
        List<AnalyticsBreakdownItemResponse> teacherApprovalBreakdown,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> paymentStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> reportStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> reviewStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> supportTicketStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> aiStatusBreakdown) {

    public record Overview(
            long totalStudents,
            long totalTeachers,
            long pendingTeacherApprovals,
            long approvedTeachers,
            long rejectedTeachers,
            long publishedContentItems,
            BigDecimal totalRevenue,
            long pendingRefunds,
            long pendingPayouts,
            long openSupportTickets,
            long pendingReports,
            long moderationActions,
            long auditLogEntries,
            long adminUnreadNotifications,
            long aiRequests) {
    }
}
