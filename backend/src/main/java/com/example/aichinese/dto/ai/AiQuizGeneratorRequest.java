package com.example.aichinese.dto.ai;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiQuizGeneratorRequest(
        @NotBlank(message = "Topic is required.")
        @Size(max = 200, message = "Topic must not exceed 200 characters.")
        String topic,
        @Size(max = 100, message = "Level must not exceed 100 characters.")
        String level,
        @Size(max = 100, message = "Category must not exceed 100 characters.")
        String category,
        @Min(value = 1, message = "Number of questions must be at least 1.")
        @Max(value = 10, message = "Number of questions must not exceed 10.")
        Integer numberOfQuestions,
        @Size(max = 10, message = "Question types must not exceed 10 items.")
        List<@Size(max = 40, message = "Question type must not exceed 40 characters.") String> questionTypes,
        @Size(max = 20, message = "Target role context must not exceed 20 characters.")
        String targetRoleContext,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {
}
