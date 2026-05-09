package com.example.aichinese.dto.learning;

import java.util.List;

public record StudentProgressSummaryResponse(
        int totalLessonsStarted,
        int completedLessons,
        int quizAverage,
        int overallCompletionPercent,
        List<String> weakAreas,
        String lastActivitySummary,
        List<StudentProgressLessonResponse> recentLessons) {
}
