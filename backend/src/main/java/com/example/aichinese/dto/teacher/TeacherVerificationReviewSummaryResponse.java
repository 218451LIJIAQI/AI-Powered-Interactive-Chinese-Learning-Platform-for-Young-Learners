package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.TeacherStatus;

public record TeacherVerificationReviewSummaryResponse(
        Long id,
        TeacherStatus decisionStatus,
        String note,
        String reviewerName,
        LocalDateTime reviewedAt) {
}
