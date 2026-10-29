package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
        @NotBlank(message = "Message is required.")
        @Size(max = 2000, message = "Message must not exceed 2000 characters.")
        String message,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference,
        @Positive(message = "Session id must be positive.")
        Long sessionId) {

    public AiChatRequest {
        message = strip(message);
        languagePreference = stripToNull(languagePreference);
    }
}
