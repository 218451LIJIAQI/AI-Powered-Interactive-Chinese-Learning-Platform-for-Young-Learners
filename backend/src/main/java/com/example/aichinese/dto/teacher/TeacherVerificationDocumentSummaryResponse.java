package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;

public record TeacherVerificationDocumentSummaryResponse(
        Long id,
        String documentType,
        String description,
        String originalFileName,
        String contentType,
        Long fileSize,
        LocalDateTime submittedAt,
        boolean currentVersion) {
}
