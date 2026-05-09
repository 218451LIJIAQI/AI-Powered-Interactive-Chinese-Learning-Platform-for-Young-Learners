package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        String targetType,
        Long targetId,
        String targetTitle,
        String reason,
        String reportStatus,
        String reporterName,
        String reporterEmail,
        Long relatedTeacherId,
        String relatedTeacherName,
        boolean teacherRevisionRequested,
        String adminNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
