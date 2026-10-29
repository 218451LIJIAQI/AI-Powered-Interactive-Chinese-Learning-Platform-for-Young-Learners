package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A student's report of an AI answer. An Ask SparkBot answer is named by {@code chatMessageId} and a practice score
 * by {@code practiceCheckId} (the server attaches what was asked and answered); a Xiaomei reply, a quiz or a story is
 * attached as the snapshots the page shows.
 */
public record CreateAiResponseReportRequest(
        @Positive(message = "Chat message id must be positive.")
        Long chatMessageId,

        @Positive(message = "Practice check id must be positive.")
        Long practiceCheckId,

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
        description = stripToNull(description);
        promptSnapshot = stripToNull(promptSnapshot);
        aiResponseSnapshot = stripToNull(aiResponseSnapshot);
    }

    @Override
    public String toString() {
        return "CreateAiResponseReportRequest["
                + "chatMessageId=" + chatMessageId
                + ", practiceCheckId=" + practiceCheckId
                + ", featureKey=" + featureKey
                + ", reasonCategory=" + reasonCategory
                + ", description=" + redactLength(description)
                + ", promptSnapshot=" + redactLength(promptSnapshot)
                + ", aiResponseSnapshot=" + redactLength(aiResponseSnapshot)
                + "]";
    }
}
