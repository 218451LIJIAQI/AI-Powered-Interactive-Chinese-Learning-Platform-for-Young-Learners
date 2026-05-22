package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

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
}
