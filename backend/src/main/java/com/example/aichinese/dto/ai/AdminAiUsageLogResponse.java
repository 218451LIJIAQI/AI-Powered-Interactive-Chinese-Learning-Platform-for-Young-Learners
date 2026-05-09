package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

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
}
