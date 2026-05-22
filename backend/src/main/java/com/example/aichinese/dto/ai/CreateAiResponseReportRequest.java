package com.example.aichinese.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAiResponseReportRequest(
        @Positive(message = "AI usage log id must be positive.")
        Long aiUsageLogId,
        @Positive(message = "Chat session id must be positive.")
        Long chatSessionId,
        @Positive(message = "Chat message id must be positive.")
        Long chatMessageId,

        @NotBlank(message = "AI feature key is required.")
        @Size(max = 100, message = "AI feature key must be 100 characters or fewer.")
        String featureKey,

        @NotBlank(message = "Report reason category is required.")
        @Size(max = 40, message = "Report reason category must be 40 characters or fewer.")
        String reasonCategory,

        @Size(max = 1000, message = "Description must be 1000 characters or fewer.")
        String description,

        @Size(max = 4000, message = "Prompt snapshot must be 4000 characters or fewer.")
        String promptSnapshot,

        @Size(max = 4000, message = "AI response snapshot must be 4000 characters or fewer.")
        String aiResponseSnapshot) {
}
