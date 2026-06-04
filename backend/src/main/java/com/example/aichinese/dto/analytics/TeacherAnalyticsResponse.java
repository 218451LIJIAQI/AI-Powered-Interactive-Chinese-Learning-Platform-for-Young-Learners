package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "overview",
        "quizScoreTrend",
        "earningsTrend",
        "contentStatusBreakdown"
})
public record TeacherAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> quizScoreTrend,
        List<AnalyticsTrendPointResponse> earningsTrend,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown) {

    public TeacherAnalyticsResponse {
        Objects.requireNonNull(overview, "overview must not be null");
        quizScoreTrend = copyRequired(quizScoreTrend, "quizScoreTrend");
        earningsTrend = copyRequired(earningsTrend, "earningsTrend");
        contentStatusBreakdown = copyRequired(contentStatusBreakdown, "contentStatusBreakdown");
    }

    private static <T> List<T> copyRequired(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(values, fieldName + " must not be null"));
    }

    @JsonPropertyOrder({
            "totalModules",
            "totalLessons",
            "totalPackages",
            "publishedContentItems",
            "submittedContentItems",
            "totalActiveStudents",
            "averageQuizScore",
            "netEarnings",
            "availableBalance",
            "currencyCode",
            "upcomingLiveClasses",
            "totalLiveEnrolments"
    })
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
            String currencyCode,
            long upcomingLiveClasses,
            long totalLiveEnrolments) {

        public Overview {
            if (averageQuizScore == null) {
                averageQuizScore = BigDecimal.ZERO;
            }
            if (netEarnings == null) {
                netEarnings = BigDecimal.ZERO;
            }
            if (availableBalance == null) {
                availableBalance = BigDecimal.ZERO;
            }
        }
    }
}
