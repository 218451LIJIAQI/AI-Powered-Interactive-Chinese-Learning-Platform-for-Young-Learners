package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.dto.teacher.TeacherVerificationDocumentSummaryResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationReviewSummaryResponse;
import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({
        "teacherId",
        "userId",
        "fullName",
        "email",
        "bio",
        "shortIntroduction",
        "teachingCategories",
        "teacherStatus",
        "rejectionReason",
        "verifiedAt",
        "verificationDocuments",
        "verificationReviewHistory",
        "recentAuditLogs",
        "createdAt",
        "updatedAt"
})
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

    public AdminTeacherDetailResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        teachingCategories = teachingCategories == null
                ? List.of()
                : List.copyOf(teachingCategories);
        verificationDocuments = verificationDocuments == null
                ? List.of()
                : List.copyOf(verificationDocuments);
        verificationReviewHistory = verificationReviewHistory == null
                ? List.of()
                : List.copyOf(verificationReviewHistory);
        recentAuditLogs = recentAuditLogs == null
                ? List.of()
                : List.copyOf(recentAuditLogs);
    }
}
