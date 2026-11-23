package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;

/** A report in the admin queue: who reported which feature and why, what was reported, and the decision. */
public record AdminAiResponseReportResponse(
        Long id,
        String studentName,
        String studentEmail,
        AiFeatureKey featureKey,
        AiResponseReportReason reasonCategory,
        String description,
        String promptSnapshot,
        String aiResponseSnapshot,
        AiResponseReportStatus status,
        String reviewerName,
        String reviewerNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {

    @Override
    public String toString() {
        return "AdminAiResponseReportResponse["
                + "id=" + id
                + ", studentName=" + redactValue(studentName)
                + ", studentEmail=" + redactValue(studentEmail)
                + ", featureKey=" + featureKey
                + ", reasonCategory=" + reasonCategory
                + ", description=" + redactLength(description)
                + ", promptSnapshot=" + redactLength(promptSnapshot)
                + ", aiResponseSnapshot=" + redactLength(aiResponseSnapshot)
                + ", status=" + status
                + ", reviewerName=" + redactValue(reviewerName)
                + ", reviewerNote=" + redactLength(reviewerNote)
                + ", reviewedAt=" + reviewedAt
                + ", createdAt=" + createdAt
                + "]";
    }
}
