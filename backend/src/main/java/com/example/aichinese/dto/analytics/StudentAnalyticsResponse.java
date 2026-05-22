package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public record StudentAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> quizScoreTrend,
        List<AnalyticsTrendPointResponse> activityTrend,
        List<AnalyticsBreakdownItemResponse> lessonStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> weakAreaBreakdown,
        List<AnalyticsBreakdownItemResponse> learningModeBreakdown) {

    public record Overview(
            long completedLessons,
            long inProgressLessons,
            long totalTrackedLessons,
            BigDecimal averageQuizScore,
            long completedVideos,
            long upcomingLiveClasses,
            long unreadNotifications) {
    }
}
