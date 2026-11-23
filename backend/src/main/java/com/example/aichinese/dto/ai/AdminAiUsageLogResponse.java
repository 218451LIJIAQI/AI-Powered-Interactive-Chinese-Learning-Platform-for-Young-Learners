package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;

/** One row of the admin AI usage log: who, which feature, the result and, for a failure, why. */
public record AdminAiUsageLogResponse(
        Long id,
        String userName,
        String userRole,
        AiFeatureKey featureKey,
        AiRequestStatus status,
        String errorSummary,
        LocalDateTime createdAt) {

    @Override
    public String toString() {
        return "AdminAiUsageLogResponse["
                + "id=" + id
                + ", userName=" + redactValue(userName)
                + ", userRole=" + userRole
                + ", featureKey=" + featureKey
                + ", status=" + status
                + ", errorSummary=" + redactLength(errorSummary)
                + ", createdAt=" + createdAt
                + "]";
    }
}
