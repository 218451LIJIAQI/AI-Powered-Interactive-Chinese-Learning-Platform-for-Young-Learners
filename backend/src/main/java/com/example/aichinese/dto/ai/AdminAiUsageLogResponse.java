package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "userId",
        "userName",
        "userEmail",
        "userRole",
        "featureKey",
        "providerName",
        "modelName",
        "status",
        "tokenCount",
        "languagePreference",
        "inputSummary",
        "outputSummary",
        "errorSummary",
        "relatedEntityType",
        "relatedEntityId",
        "createdAt"
})
public record AdminAiUsageLogResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String userRole,
        String featureKey,
        String providerName,
        String modelName,
        String status,
        Integer tokenCount,
        String languagePreference,
        String inputSummary,
        String outputSummary,
        String errorSummary,
        String relatedEntityType,
        Long relatedEntityId,
        LocalDateTime createdAt) {

    private static final String REDACTED = "<redacted>";

    @Override
    public String toString() {
        return "AdminAiUsageLogResponse["
                + "id=" + id
                + ", userId=" + userId
                + ", userName=" + redactValue(userName)
                + ", userEmail=" + redactValue(userEmail)
                + ", userRole=" + userRole
                + ", featureKey=" + featureKey
                + ", providerName=" + providerName
                + ", modelName=" + modelName
                + ", status=" + status
                + ", tokenCount=" + tokenCount
                + ", languagePreference=" + languagePreference
                + ", inputSummary=" + redactLength(inputSummary)
                + ", outputSummary=" + redactLength(outputSummary)
                + ", errorSummary=" + redactLength(errorSummary)
                + ", relatedEntityType=" + relatedEntityType
                + ", relatedEntityId=" + relatedEntityId
                + ", createdAt=" + createdAt
                + "]";
    }

    private static String redactValue(String value) {
        return value == null ? "null" : REDACTED;
    }

    private static String redactLength(String value) {
        return value == null ? "null" : "<redacted:" + value.length() + ">";
    }
}
