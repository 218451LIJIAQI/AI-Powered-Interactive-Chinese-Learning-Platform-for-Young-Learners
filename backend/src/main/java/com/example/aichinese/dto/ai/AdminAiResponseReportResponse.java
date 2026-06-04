package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "studentUserId",
        "studentName",
        "studentEmail",
        "aiUsageLogId",
        "chatSessionId",
        "chatMessageId",
        "featureKey",
        "reasonCategory",
        "description",
        "promptSnapshot",
        "aiResponseSnapshot",
        "status",
        "reviewerUserId",
        "reviewerName",
        "reviewerNote",
        "reviewedAt",
        "createdAt",
        "updatedAt"
})
public record AdminAiResponseReportResponse(
        Long id,
        Long studentUserId,
        String studentName,
        String studentEmail,
        Long aiUsageLogId,
        Long chatSessionId,
        Long chatMessageId,
        String featureKey,
        String reasonCategory,
        String description,
        String promptSnapshot,
        String aiResponseSnapshot,
        String status,
        Long reviewerUserId,
        String reviewerName,
        String reviewerNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    @Override
    public String toString() {
        return "AdminAiResponseReportResponse["
                + "id=" + id
                + ", studentUserId=" + studentUserId
                + ", studentName=" + redactValue(studentName)
                + ", studentEmail=" + redactValue(studentEmail)
                + ", aiUsageLogId=" + aiUsageLogId
                + ", chatSessionId=" + chatSessionId
                + ", chatMessageId=" + chatMessageId
                + ", featureKey=" + featureKey
                + ", reasonCategory=" + reasonCategory
                + ", description=" + redactLength(description)
                + ", promptSnapshot=" + redactLength(promptSnapshot)
                + ", aiResponseSnapshot=" + redactLength(aiResponseSnapshot)
                + ", status=" + status
                + ", reviewerUserId=" + reviewerUserId
                + ", reviewerName=" + redactValue(reviewerName)
                + ", reviewerNote=" + redactLength(reviewerNote)
                + ", reviewedAt=" + reviewedAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + "]";
    }

    private static String redactValue(String value) {
        return value == null ? "null" : "<redacted>";
    }

    private static String redactLength(String value) {
        return value == null ? "null" : "<redacted:" + value.length() + ">";
    }
}
