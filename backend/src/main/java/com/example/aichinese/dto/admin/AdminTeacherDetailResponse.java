package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.dto.teacher.TeacherVerificationDocumentSummaryResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationReviewSummaryResponse;
import com.example.aichinese.entity.enums.TeacherStatus;

public record AdminTeacherDetailResponse(
        Long teacherId,
        Long userId,
        String fullName,
        String email,
        String bio,
        String shortIntroduction,
        List<String> teachingCategories,
        TeacherStatus teacherStatus,
        String rejectionReason,
        LocalDateTime verifiedAt,
        List<TeacherVerificationDocumentSummaryResponse> verificationDocuments,
        List<TeacherVerificationReviewSummaryResponse> verificationReviewHistory,
        List<AdminAuditSummaryResponse> recentAuditLogs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
