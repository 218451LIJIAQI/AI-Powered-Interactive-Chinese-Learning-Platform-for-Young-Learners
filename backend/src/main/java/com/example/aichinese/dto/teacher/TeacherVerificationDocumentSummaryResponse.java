package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "documentType",
        "description",
        "originalFileName",
        "contentType",
        "fileSize",
        "submittedAt",
        "currentVersion"
})
public record TeacherVerificationDocumentSummaryResponse(
        Long id,
        String documentType,
        String description,
        String originalFileName,
        String contentType,
        Long fileSize,
        LocalDateTime submittedAt,
        boolean currentVersion) {

    public TeacherVerificationDocumentSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        if (fileSize != null && fileSize < 0L) {
            throw new IllegalArgumentException("fileSize must be non-negative, got " + fileSize);
        }
    }
}
