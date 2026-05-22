package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public record TeacherAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> quizScoreTrend,
        List<AnalyticsTrendPointResponse> earningsTrend,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown) {

    public record Overview(
            long totalModules,
            long totalLessons,
            long totalPackages,
            long publishedContentItems,
            long submittedContentItems,
            long totalActiveStudents,
            BigDecimal averageQuizScore,
            BigDecimal netEarnings,
            BigDecimal availableBalance,
            long upcomingLiveClasses,
            long totalLiveEnrolments) {
    }
}
