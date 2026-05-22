package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

public record StudentAiResponseReportResponse(
        Long id,
        Long aiUsageLogId,
        Long chatSessionId,
        Long chatMessageId,
        String featureKey,
        String reasonCategory,
        String description,
        String promptSnapshot,
        String aiResponseSnapshot,
        String status,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
