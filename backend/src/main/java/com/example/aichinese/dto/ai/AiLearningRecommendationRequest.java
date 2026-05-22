package com.example.aichinese.dto.ai;

import java.util.List;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AiLearningRecommendationRequest(
        @Positive(message = "Student id must be positive.")
        Long studentId,
        @Size(max = 100, message = "Current level must not exceed 100 characters.")
        String currentLevel,
        @Size(max = 20, message = "Recent quiz results must not exceed 20 items.")
        List<@Size(max = 200, message = "Recent quiz result must not exceed 200 characters.") String> recentQuizResults,
        @Size(max = 20, message = "Wrong answers must not exceed 20 items.")
        List<@Size(max = 200, message = "Wrong answer must not exceed 200 characters.") String> wrongAnswers,
        @Size(max = 20, message = "Weak areas must not exceed 20 items.")
        List<@Size(max = 200, message = "Weak area must not exceed 200 characters.") String> weakAreas,
        @Size(max = 20, message = "Completed lessons must not exceed 20 items.")
        List<@Size(max = 200, message = "Completed lesson must not exceed 200 characters.") String> completedLessons,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {
}
