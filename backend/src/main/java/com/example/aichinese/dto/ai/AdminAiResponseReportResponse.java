package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

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
}
