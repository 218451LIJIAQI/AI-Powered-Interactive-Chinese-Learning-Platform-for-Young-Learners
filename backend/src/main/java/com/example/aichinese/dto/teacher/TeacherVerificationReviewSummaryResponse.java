package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({ "id", "decisionStatus", "note", "reviewerName", "reviewedAt" })
public record TeacherVerificationReviewSummaryResponse(
        Long id,
        TeacherStatus decisionStatus,
        String note,
        String reviewerName,
        LocalDateTime reviewedAt) {

    public TeacherVerificationReviewSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(decisionStatus, "decisionStatus must not be null");
        Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
    }
}
