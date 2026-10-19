package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "overview",
        "quizScoreTrend",
        "contentStatusBreakdown"
})
public record TeacherAnalyticsResponse(
        Overview overview,
        List<AnalyticsTrendPointResponse> quizScoreTrend,
        List<AnalyticsBreakdownItemResponse> contentStatusBreakdown) {

    public TeacherAnalyticsResponse {
        Objects.requireNonNull(overview, "overview must not be null");
        quizScoreTrend = copyRequired(quizScoreTrend, "quizScoreTrend");
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
            long upcomingLiveClasses,
            long totalLiveEnrolments) {

        public Overview {
            if (averageQuizScore == null) {
                averageQuizScore = BigDecimal.ZERO;
            }
        }
    }
}
