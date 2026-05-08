package com.example.aichinese.mapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.admin.AdminAuditSummaryResponse;
import com.example.aichinese.dto.admin.AdminStudentListItemResponse;
import com.example.aichinese.dto.admin.AdminTeacherDetailResponse;
import com.example.aichinese.dto.admin.AdminTeacherListItemResponse;
import com.example.aichinese.dto.admin.AdminUserListItemResponse;
import com.example.aichinese.dto.publicview.PublicTeacherProfileResponse;
import com.example.aichinese.dto.teacher.TeacherProfileResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationDocumentSummaryResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationReviewSummaryResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.entity.TeacherVerificationReview;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;

@Component
public class TeacherManagementMapper {

    public TeacherProfileResponse toTeacherProfileResponse(
            TeacherProfile profile,
            List<TeacherVerificationDocument> documents) {
        return new TeacherProfileResponse(
                profile.getUser().getId(),
                profile.getUser().getDisplayName(),
                profile.getUser().getEmail(),
                profile.getId(),
                profile.getBio(),
                profile.getQualificationSummary(),
                splitCategories(profile.getTeachingCategories()),
                profile.getTeacherStatus(),
                profile.getRejectionReason(),
                documents.stream().map(this::toDocumentSummary).toList(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    public TeacherVerificationDocumentSummaryResponse toDocumentSummary(TeacherVerificationDocument document) {
        return new TeacherVerificationDocumentSummaryResponse(
                document.getId(),
                document.getDocumentType(),
                document.getDocumentNote(),
                document.getUploadedFile().getFileName(),
                document.getUploadedFile().getContentType(),
                document.getUploadedFile().getFileSize(),
                document.getSubmittedAt(),
                document.isCurrentVersion());
    }

    public TeacherVerificationReviewSummaryResponse toReviewSummary(TeacherVerificationReview review) {
        return new TeacherVerificationReviewSummaryResponse(
                review.getId(),
                review.getDecisionStatus(),
                review.getReviewNote(),
                review.getReviewerUser() != null ? review.getReviewerUser().getDisplayName() : null,
                review.getReviewedAt());
    }

    public AdminTeacherListItemResponse toAdminTeacherListItem(
            TeacherProfile profile,
            long documentCount,
            TeacherVerificationReview latestReview,
            TeacherVerificationDocument latestDocument) {
        return new AdminTeacherListItemResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getDisplayName(),
                profile.getUser().getEmail(),
                profile.getTeacherStatus(),
                summarize(profile.getBio(), 140),
                documentCount,
                latestReview != null ? latestReview.getReviewedAt() : null,
                latestDocument != null ? latestDocument.getSubmittedAt() : null,
                profile.getCreatedAt());
    }

    public AdminTeacherDetailResponse toAdminTeacherDetailResponse(
            TeacherProfile profile,
            List<TeacherVerificationDocument> documents,
            List<TeacherVerificationReview> reviews,
            List<AuditLog> auditLogs) {
        return new AdminTeacherDetailResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getDisplayName(),
                profile.getUser().getEmail(),
                profile.getBio(),
                profile.getQualificationSummary(),
                splitCategories(profile.getTeachingCategories()),
                profile.getTeacherStatus(),
                profile.getRejectionReason(),
                profile.getVerifiedAt(),
                documents.stream().map(this::toDocumentSummary).toList(),
                reviews.stream().map(this::toReviewSummary).toList(),
                auditLogs.stream().map(this::toAuditSummary).toList(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    public AdminUserListItemResponse toAdminUserListItem(User user, TeacherProfile teacherProfile) {
        return new AdminUserListItemResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .sorted(Comparator.naturalOrder())
                        .toList(),
                teacherProfile != null ? teacherProfile.getTeacherStatus() : null,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    public AdminStudentListItemResponse toAdminStudentListItem(StudentProfile studentProfile) {
        return new AdminStudentListItemResponse(
                studentProfile.getId(),
                studentProfile.getUser().getId(),
                studentProfile.getUser().getDisplayName(),
                studentProfile.getUser().getEmail(),
                studentProfile.getUser().getPreferredLanguage(),
                studentProfile.getCreatedAt());
    }

    public PublicTeacherProfileResponse toPublicTeacherProfileResponse(TeacherProfile teacherProfile) {
        return new PublicTeacherProfileResponse(
                teacherProfile.getId(),
                teacherProfile.getUser().getDisplayName(),
                teacherProfile.getBio(),
                teacherProfile.getTeacherStatus() == com.example.aichinese.entity.enums.TeacherStatus.APPROVED,
                splitCategories(teacherProfile.getTeachingCategories()),
                0,
                Optional.ofNullable(teacherProfile.getAverageRating()).orElse(java.math.BigDecimal.ZERO),
                0);
    }

    public AdminAuditSummaryResponse toAuditSummary(AuditLog auditLog) {
        return new AdminAuditSummaryResponse(
                auditLog.getId(),
                auditLog.getActionType(),
                auditLog.getTargetType(),
                auditLog.getDescription(),
                auditLog.getCreatedAt());
    }

    public String joinCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }

        return categories.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .map(value -> value.replace(",", ""))
                .distinct()
                .limit(12)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    public List<String> splitCategories(String categories) {
        if (categories == null || categories.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(categories.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public boolean hasRole(User user, RoleName roleName) {
        return user.getRoles().stream()
                .anyMatch(role -> role.getName() == roleName);
    }

    private String summarize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }

        return trimmed.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }
}
