package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "topic", "level", "languagePreference", "lengthPreference" })
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

    public AiStoryGeneratorRequest {
        topic = strip(topic);
        level = strip(level);
        languagePreference = strip(languagePreference);
        lengthPreference = strip(lengthPreference);
    }
}
