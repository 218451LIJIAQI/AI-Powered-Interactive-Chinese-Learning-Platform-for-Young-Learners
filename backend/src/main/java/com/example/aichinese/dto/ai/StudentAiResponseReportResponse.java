package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;

/** One of the student's own AI reports, with the admin's note once it is resolved or dismissed. */
public record StudentAiResponseReportResponse(
        Long id,
        AiFeatureKey featureKey,
        AiResponseReportReason reasonCategory,
        String promptSnapshot,
        String aiResponseSnapshot,
        AiResponseReportStatus status,
        String reviewerNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {

    @Override
    public String toString() {
        return "StudentAiResponseReportResponse["
                + "id=" + id
                + ", featureKey=" + featureKey
                + ", reasonCategory=" + reasonCategory
                + ", promptSnapshot=" + redactLength(promptSnapshot)
                + ", aiResponseSnapshot=" + redactLength(aiResponseSnapshot)
                + ", status=" + status
                + ", reviewerNote=" + redactLength(reviewerNote)
                + ", reviewedAt=" + reviewedAt
                + ", createdAt=" + createdAt
                + "]";
    }
}
