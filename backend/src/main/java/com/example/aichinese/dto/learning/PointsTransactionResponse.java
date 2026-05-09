package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

public record PointsTransactionResponse(
        Long id,
        Integer pointsDelta,
        String reason,
        String sourceType,
        Long sourceId,
        LocalDateTime createdAt) {
}
