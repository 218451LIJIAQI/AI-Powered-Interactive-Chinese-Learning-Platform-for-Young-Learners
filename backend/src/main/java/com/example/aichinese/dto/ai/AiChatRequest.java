package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "message", "languagePreference", "sessionId", "context", "relatedLessonId" })
public record AiChatRequest(
        @NotBlank(message = "Message is required.")
        @Size(max = 2000, message = "Message must not exceed 2000 characters.")
        String message,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference,
        @Positive(message = "Session id must be positive.")
        Long sessionId,
        @Size(max = 1000, message = "Context must not exceed 1000 characters.")
        String context,
        @Positive(message = "Related lesson id must be positive.")
        Long relatedLessonId) {

    public AiChatRequest {
        message = strip(message);
        languagePreference = strip(languagePreference);
        context = strip(context);
    }
}
