package com.example.aichinese.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
        @NotBlank(message = "Message is required.")
        @Size(max = 2000, message = "Message must not exceed 2000 characters.")
        String message,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference,
        Long sessionId,
        @Size(max = 1000, message = "Context must not exceed 1000 characters.")
        String context,
        Long relatedLessonId) {
}
