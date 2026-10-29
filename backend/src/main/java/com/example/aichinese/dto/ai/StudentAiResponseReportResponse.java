package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import java.time.LocalDateTime;

/** One of the student's own AI reports, with the admin's note once it is resolved or dismissed. */
public record StudentAiResponseReportResponse(
        Long id,
        String featureKey,
        String reasonCategory,
        String description,
        String promptSnapshot,
        String aiResponseSnapshot,
        String status,
        String reviewerNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {

    @Override
    public String toString() {
        return "StudentAiResponseReportResponse["
                + "id=" + id
                + ", featureKey=" + featureKey
                + ", reasonCategory=" + reasonCategory
                + ", description=" + redactLength(description)
                + ", promptSnapshot=" + redactLength(promptSnapshot)
                + ", aiResponseSnapshot=" + redactLength(aiResponseSnapshot)
                + ", status=" + status
                + ", reviewerNote=" + redactLength(reviewerNote)
                + ", reviewedAt=" + reviewedAt
                + ", createdAt=" + createdAt
                + "]";
    }
}
