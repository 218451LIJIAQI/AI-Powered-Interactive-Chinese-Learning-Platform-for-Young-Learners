package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "actorUserId",
        "actorEmail",
        "actionType",
        "targetType",
        "targetId",
        "description",
        "ipAddress",
        "requestId",
        "createdAt"
})
public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String actorEmail,
        String actionType,
        String targetType,
        Long targetId,
        String description,
        String ipAddress,
            String requestId,
            LocalDateTime createdAt) {

    public AuditLogResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(actionType, "actionType must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
