package com.example.aichinese.dto.teacher;

import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

public record TeacherVerificationStatusResponse(
        TeacherStatus teacherStatus,
        List<TeacherVerificationDocumentSummaryResponse> latestSubmittedDocuments,
        TeacherVerificationReviewSummaryResponse latestReviewResult,
        String rejectionReason,
        boolean resubmissionAllowed) {
}
