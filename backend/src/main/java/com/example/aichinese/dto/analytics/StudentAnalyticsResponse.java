package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "overview",
        "quizScoreTrend",
        "activityTrend",
        "lessonStatusBreakdown",
        "weakAreaBreakdown",
        "learningModeBreakdown"
})
public record StudentAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> quizScoreTrend,
        List<AnalyticsTrendPointResponse> activityTrend,
        List<AnalyticsBreakdownItemResponse> lessonStatusBreakdown,
        List<AnalyticsBreakdownItemResponse> weakAreaBreakdown,
        List<AnalyticsBreakdownItemResponse> learningModeBreakdown) {

    public StudentAnalyticsResponse {
        Objects.requireNonNull(overview, "overview must not be null");
        quizScoreTrend = copyRequired(quizScoreTrend, "quizScoreTrend");
        activityTrend = copyRequired(activityTrend, "activityTrend");
        lessonStatusBreakdown = copyRequired(lessonStatusBreakdown, "lessonStatusBreakdown");
        weakAreaBreakdown = copyRequired(weakAreaBreakdown, "weakAreaBreakdown");
        learningModeBreakdown = copyRequired(learningModeBreakdown, "learningModeBreakdown");
    }

    private static <T> List<T> copyRequired(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(values, fieldName + " must not be null"));
    }

    @JsonPropertyOrder({
            "completedLessons",
            "inProgressLessons",
            "totalTrackedLessons",
            "averageQuizScore",
            "completedVideos",
            "upcomingLiveClasses",
            "unreadNotifications"
    })
    public record Overview(
            long completedLessons,
            long inProgressLessons,
            long totalTrackedLessons,
            BigDecimal averageQuizScore,
            long completedVideos,
            long upcomingLiveClasses,
            long unreadNotifications) {

        public Overview {
            if (averageQuizScore == null) {
                averageQuizScore = BigDecimal.ZERO;
            }
        }
    }
}
