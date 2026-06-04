package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({
        "aiUsageLogId",
        "chatSessionId",
        "chatMessageId",
        "featureKey",
        "reasonCategory",
        "description",
        "promptSnapshot",
        "aiResponseSnapshot"
})
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

    public CreateAiResponseReportRequest {
        featureKey = strip(featureKey);
        reasonCategory = strip(reasonCategory);
        description = strip(description);
        promptSnapshot = strip(promptSnapshot);
        aiResponseSnapshot = strip(aiResponseSnapshot);
    }

    @Override
    public String toString() {
        return "CreateAiResponseReportRequest["
                + "aiUsageLogId=" + aiUsageLogId
                + ", chatSessionId=" + chatSessionId
                + ", chatMessageId=" + chatMessageId
                + ", featureKey=" + featureKey
                + ", reasonCategory=" + reasonCategory
                + ", description=" + redactLength(description)
                + ", promptSnapshot=" + redactLength(promptSnapshot)
                + ", aiResponseSnapshot=" + redactLength(aiResponseSnapshot)
                + "]";
    }

    private static String redactLength(String value) {
        return value == null ? "null" : "<redacted:" + value.length() + ">";
    }
}
