package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        String type,
        String status,
        String targetPath,
        LocalDateTime createdAt,
        LocalDateTime readAt) {

    public NotificationResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
