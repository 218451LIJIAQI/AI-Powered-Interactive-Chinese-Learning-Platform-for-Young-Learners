package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiStoryGeneratorRequest(
        @NotBlank(message = "Topic is required.")
        @Size(max = 200, message = "Topic must not exceed 200 characters.")
        String topic,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    public AiStoryGeneratorRequest {
        topic = strip(topic);
        languagePreference = stripToNull(languagePreference);
    }
}
