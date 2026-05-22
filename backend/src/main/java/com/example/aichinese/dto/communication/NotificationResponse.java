package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        String type,
        String status,
        String targetType,
        Long targetId,
        String targetPath,
        LocalDateTime createdAt,
        LocalDateTime readAt) {
}
