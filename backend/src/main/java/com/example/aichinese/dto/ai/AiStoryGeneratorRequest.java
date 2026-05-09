package com.example.aichinese.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiStoryGeneratorRequest(
        @NotBlank(message = "Topic is required.")
        @Size(max = 200, message = "Topic must not exceed 200 characters.")
        String topic,
        @Size(max = 100, message = "Level must not exceed 100 characters.")
        String level,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference,
        @Size(max = 50, message = "Length preference must not exceed 50 characters.")
        String lengthPreference) {
}
