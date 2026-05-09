package com.example.aichinese.dto.ai;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiProgressInsightRequest(
        Long studentId,
        @Size(max = 1000, message = "Progress summary must not exceed 1000 characters.")
        String progressSummary,
        List<String> quizHistory,
        List<String> weakAreas,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference,
        @NotBlank(message = "Audience is required.")
        @Size(max = 20, message = "Audience must not exceed 20 characters.")
        String audience) {
}
