package com.example.aichinese.dto.communication;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        String targetType,
        Long targetId,
        String targetTitle,
        BigDecimal rating,
        String comment,
        String reviewerName,
        String reviewStatus,
        boolean visible,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
