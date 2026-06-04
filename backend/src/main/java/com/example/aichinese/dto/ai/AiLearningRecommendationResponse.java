package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "recommendedNextStep",
        "recommendedLessonOrActivity",
        "weakAreaSummary",
        "revisionSuggestion",
        "shortEncouragement",
        "suggestedPracticeTypes",
        "status"
})
public record AiLearningRecommendationResponse(
        String recommendedNextStep,
        String recommendedLessonOrActivity,
        String weakAreaSummary,
        String revisionSuggestion,
        String shortEncouragement,
        List<String> suggestedPracticeTypes,
        String status) {

    public AiLearningRecommendationResponse {
        suggestedPracticeTypes = suggestedPracticeTypes == null
                ? List.of()
                : List.copyOf(suggestedPracticeTypes);
    }
}
