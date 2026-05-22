package com.example.aichinese.dto.learning;

public record StudentQuizSummaryResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        Integer timeLimitMinutes,
        Integer totalPoints,
        int questionCount) {
}
