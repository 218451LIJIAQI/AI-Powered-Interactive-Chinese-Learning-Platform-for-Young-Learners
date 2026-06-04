package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "auditLogId", "actionType", "targetType", "description", "createdAt" })
public record AdminAuditSummaryResponse(
        Long auditLogId,
        String actionType,
        String targetType,
        String description,
        LocalDateTime createdAt) {

    public AdminAuditSummaryResponse {
        Objects.requireNonNull(auditLogId, "auditLogId must not be null");
        Objects.requireNonNull(actionType, "actionType must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
