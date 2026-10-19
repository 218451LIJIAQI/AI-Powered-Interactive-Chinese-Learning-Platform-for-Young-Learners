package com.example.aichinese.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.admin.AdminAuditSummaryResponse;
import com.example.aichinese.dto.admin.AdminTeacherDetailResponse;
import com.example.aichinese.dto.admin.AdminTeacherListItemResponse;
import com.example.aichinese.dto.admin.AdminUserListItemResponse;
import com.example.aichinese.dto.publicview.PublicTeacherProfileResponse;
import com.example.aichinese.dto.teacher.TeacherProfileResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationDocumentSummaryResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationReviewSummaryResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.entity.TeacherVerificationReview;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.util.TextUtils;

@Component
public class TeacherManagementMapper {

    private static final int MAX_CATEGORIES = 12;

    private static final int BIO_SUMMARY_MAX_LENGTH = 140;

    private static final String CATEGORY_DELIMITER = ", ";

    public TeacherProfileResponse toTeacherProfileResponse(
            TeacherProfile profile,
            List<TeacherVerificationDocument> documents) {
        User user = profile.getUser();
        return new TeacherProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                profile.getId(),
                profile.getBio(),
                profile.getQualificationSummary(),
                TextUtils.splitCsv(profile.getTeachingCategories()),
                profile.getTeacherStatus(),
                profile.getRejectionReason(),
                mapList(documents, this::toDocumentSummary),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    public TeacherVerificationDocumentSummaryResponse toDocumentSummary(TeacherVerificationDocument document) {
        UploadedFile file = document.getUploadedFile();
        return new TeacherVerificationDocumentSummaryResponse(
                document.getId(),
                document.getDocumentType().name(),
                document.getDocumentNote(),
                file == null ? null : file.getFileName(),
                file == null ? null : file.getContentType(),
                file == null ? null : file.getFileSize(),
                document.getSubmittedAt(),
                document.isCurrentVersion());
    }

    public TeacherVerificationReviewSummaryResponse toReviewSummary(TeacherVerificationReview review) {
        User reviewer = review.getReviewerUser();
        return new TeacherVerificationReviewSummaryResponse(
                review.getId(),
                review.getDecisionStatus(),
                review.getReviewNote(),
                reviewer == null ? null : reviewer.getDisplayName(),
                review.getReviewedAt());
    }

    public AdminTeacherListItemResponse toAdminTeacherListItem(
            TeacherProfile profile,
            long documentCount,
            TeacherVerificationReview latestReview,
            TeacherVerificationDocument latestDocument) {
        User user = profile.getUser();
        return new AdminTeacherListItemResponse(
                profile.getId(),
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                profile.getTeacherStatus(),
                TextUtils.truncateWithEllipsis(profile.getBio(), BIO_SUMMARY_MAX_LENGTH),
                documentCount,
                latestReview == null ? null : latestReview.getReviewedAt(),
                latestDocument == null ? null : latestDocument.getSubmittedAt(),
                profile.getCreatedAt());
    }

    public AdminTeacherDetailResponse toAdminTeacherDetailResponse(
            TeacherProfile profile,
            List<TeacherVerificationDocument> documents,
            List<TeacherVerificationReview> reviews,
            List<AuditLog> auditLogs) {
        User user = profile.getUser();
        return new AdminTeacherDetailResponse(
                profile.getId(),
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                profile.getBio(),
                profile.getQualificationSummary(),
                TextUtils.splitCsv(profile.getTeachingCategories()),
                profile.getTeacherStatus(),
                profile.getRejectionReason(),
                profile.getVerifiedAt(),
                mapList(documents, this::toDocumentSummary),
                mapList(reviews, this::toReviewSummary),
                mapList(auditLogs, TeacherManagementMapper::toAuditSummary),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    public AdminUserListItemResponse toAdminUserListItem(User user, TeacherProfile teacherProfile) {
        Set<Role> roles = user.getRoles();
        List<String> roleNames = roles == null
                ? List.of()
                : roles.stream()
                        .map(role -> role.getName().name())
                        .sorted(Comparator.naturalOrder())
                        .toList();
        return new AdminUserListItemResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                roleNames,
                user.isActive(),
                teacherProfile == null ? null : teacherProfile.getTeacherStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    public PublicTeacherProfileResponse toPublicTeacherProfileResponse(TeacherProfile teacherProfile, int courseCount) {
        return new PublicTeacherProfileResponse(
                teacherProfile.getId(),
                teacherProfile.getUser().getDisplayName(),
                teacherProfile.getBio(),
                teacherProfile.getTeacherStatus() == TeacherStatus.APPROVED,
                TextUtils.splitCsv(teacherProfile.getTeachingCategories()),
                courseCount);
    }

    private static AdminAuditSummaryResponse toAuditSummary(AuditLog auditLog) {
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
        String joined = categories.stream()
                .map(TextUtils::collapseWhitespaceToNull)
                .filter(value -> value != null)
                .map(value -> value.replace(",", ""))
                .map(TextUtils::trimToNull)
                .filter(value -> value != null)
                .distinct()
                .limit(MAX_CATEGORIES)
                .collect(Collectors.joining(CATEGORY_DELIMITER));
        return joined.isEmpty() ? null : joined;
    }

    private static <T, R> List<R> mapList(List<T> source, Function<? super T, R> mapper) {
        return (source == null ? List.<T>of() : source).stream()
                .map(mapper)
                .toList();
    }
}
