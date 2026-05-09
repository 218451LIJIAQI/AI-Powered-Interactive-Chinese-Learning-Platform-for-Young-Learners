package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String actorEmail,
        String actionType,
        String targetType,
        Long targetId,
        String description,
        String ipAddress,
        LocalDateTime createdAt) {
}
