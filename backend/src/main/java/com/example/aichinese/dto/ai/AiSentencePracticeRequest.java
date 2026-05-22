package com.example.aichinese.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiSentencePracticeRequest(
        @NotBlank(message = "Sentence is required.")
        @Size(max = 1000, message = "Sentence must not exceed 1000 characters.")
        String sentence,
        @Size(max = 50, message = "Learner level must not exceed 50 characters.")
        String learnerLevel,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {
}
