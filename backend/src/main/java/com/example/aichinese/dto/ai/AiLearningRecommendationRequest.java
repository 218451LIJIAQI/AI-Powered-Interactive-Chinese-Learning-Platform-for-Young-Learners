package com.example.aichinese.dto.ai;

import java.util.List;

import jakarta.validation.constraints.Size;

public record AiLearningRecommendationRequest(
        Long studentId,
        @Size(max = 100, message = "Current level must not exceed 100 characters.")
        String currentLevel,
        List<String> recentQuizResults,
        List<String> wrongAnswers,
        List<String> weakAreas,
        List<String> completedLessons,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {
}
