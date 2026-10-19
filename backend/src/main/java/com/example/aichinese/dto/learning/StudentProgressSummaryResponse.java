package com.example.aichinese.dto.learning;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Student progress summary. {@code weakAreas} are the lesson titles of recent wrong quiz answers;
 * {@code lessons} lists every lesson with progress (newest first) with its mastery indicator (D25).
 */
@JsonPropertyOrder({
        "totalLessonsStarted",
        "completedLessons",
        "quizAverage",
        "overallCompletionPercent",
        "weakAreas",
        "lastActivitySummary",
        "recentLessons",
        "lessons"
})
public record StudentProgressSummaryResponse(
        int totalLessonsStarted,
        int completedLessons,
        int quizAverage,
        int overallCompletionPercent,
        List<String> weakAreas,
        String lastActivitySummary,
        List<StudentProgressLessonResponse> recentLessons,
        List<StudentProgressLessonResponse> lessons) {

    public StudentProgressSummaryResponse {
        requireNonNegative(totalLessonsStarted, "totalLessonsStarted");
        requireNonNegative(completedLessons, "completedLessons");
        quizAverage = Math.max(0, Math.min(100, quizAverage));
        overallCompletionPercent = Math.max(0, Math.min(100, overallCompletionPercent));
        weakAreas = weakAreas == null ? List.of() : List.copyOf(weakAreas);
        lastActivitySummary = lastActivitySummary == null ? "" : lastActivitySummary.strip();
        recentLessons = recentLessons == null ? List.of() : List.copyOf(recentLessons);
        lessons = lessons == null ? List.of() : List.copyOf(lessons);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative, was " + value);
        }
    }
}
