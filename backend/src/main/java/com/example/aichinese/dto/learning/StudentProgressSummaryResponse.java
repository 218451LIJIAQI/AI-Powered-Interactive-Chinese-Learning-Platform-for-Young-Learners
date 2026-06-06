package com.example.aichinese.dto.learning;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "totalLessonsStarted",
        "completedLessons",
        "quizAverage",
        "overallCompletionPercent",
        "weakAreas",
        "lastActivitySummary",
        "recentLessons"
})
public record StudentProgressSummaryResponse(
        int totalLessonsStarted,
        int completedLessons,
        int quizAverage,
        int overallCompletionPercent,
        List<String> weakAreas,
        String lastActivitySummary,
        List<StudentProgressLessonResponse> recentLessons) {

    public StudentProgressSummaryResponse {
        requireNonNegative(totalLessonsStarted, "totalLessonsStarted");
        requireNonNegative(completedLessons, "completedLessons");
        quizAverage = Math.max(0, Math.min(100, quizAverage));
        overallCompletionPercent = Math.max(0, Math.min(100, overallCompletionPercent));
        weakAreas = weakAreas == null ? List.of() : List.copyOf(weakAreas);
        lastActivitySummary = lastActivitySummary == null ? "" : lastActivitySummary.strip();
        recentLessons = recentLessons == null ? List.of() : List.copyOf(recentLessons);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative, was " + value);
        }
    }
}
