package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "targetType",
        "targetId",
        "targetTitle",
        "actionTaken",
        "note",
        "adminUserId",
        "adminName",
        "relatedTeacherId",
        "relatedTeacherName",
        "createdAt"
})
public record ModerationRecordResponse(
        Long id,
        String targetType,
        Long targetId,
        String targetTitle,
        String actionTaken,
        String note,
        Long adminUserId,
        String adminName,
        Long relatedTeacherId,
        String relatedTeacherName,
        LocalDateTime createdAt) {

    public ModerationRecordResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(actionTaken, "actionTaken must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
