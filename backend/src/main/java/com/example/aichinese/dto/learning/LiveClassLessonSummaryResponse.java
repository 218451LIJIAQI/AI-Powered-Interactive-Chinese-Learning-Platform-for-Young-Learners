package com.example.aichinese.dto.learning;

public record LiveClassLessonSummaryResponse(
        Long lessonId,
        String lessonTitle,
        String learningType,
        String accessType) {
}
