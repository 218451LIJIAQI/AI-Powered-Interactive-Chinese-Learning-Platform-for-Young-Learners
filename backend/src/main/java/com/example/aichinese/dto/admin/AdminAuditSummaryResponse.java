package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;

public record AdminAuditSummaryResponse(
        Long auditLogId,
        String actionType,
        String targetType,
        String description,
        LocalDateTime createdAt) {
}
