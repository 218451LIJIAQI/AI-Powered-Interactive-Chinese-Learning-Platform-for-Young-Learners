package com.example.aichinese.dto.ai;

import java.util.List;

public record AiLearningRecommendationResponse(
        String recommendedNextStep,
        String recommendedLessonOrActivity,
        String weakAreaSummary,
        String revisionSuggestion,
        String shortEncouragement,
        List<String> suggestedPracticeTypes,
        String status) {
}
