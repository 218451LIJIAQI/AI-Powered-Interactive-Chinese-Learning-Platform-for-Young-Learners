package com.example.aichinese.dto.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One audit log entry. {@code category} groups the action (ACCOUNT, CONTENT, AI, SUPPORT, LEARNING, SYSTEM) and
 * {@code targetLabel} names what the entry is about in words; both are null-safe for entries written before V14.
 */
public record AuditEntryResponse(
        Long id,
        String actionType,
        AuditCategory category,
        String actorName,
        String actorEmail,
        String targetType,
        String targetLabel,
        /** The user the target belongs to (the account itself or a teacher profile's account), for "Open …". */
        Long targetUserId,
        String reason,
        String description,
        LocalDateTime createdAt) {

    public AuditEntryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(actionType, "actionType must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
