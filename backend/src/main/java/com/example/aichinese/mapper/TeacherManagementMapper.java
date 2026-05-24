package com.example.aichinese.mapper;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.entity.TeacherVerificationReview;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Entity-to-DTO translation for the teacher and admin management
 * surfaces (teacher profile, verification documents/reviews, admin user
 * listings, public teacher cards).
 *
 * <p>The {@code teaching_categories} column stores a comma-separated
 * string for storage simplicity (vs a normalised join table) — see
 * {@link #joinCategories(List)} / {@link #splitCategories(String)} for
 * the contract. {@code joinCategories} silently de-duplicates,
 * trims commas out of individual category names (since commas are the
 * delimiter), and caps the stored list at 12 entries; the front-end
 * enforces a matching cap on the input side so a well-behaved client
 * never sees this truncation kick in.</p>
 *
 * <p>{@link #summarize(String, int)} produces ellipsis-suffixed
 * one-line summaries for admin list views, preserving up to
 * {@code maxLength - 3} content characters so the trailing
 * "{@code ...}" fits without exceeding the cap.</p>
 */
@Component
public class TeacherManagementMapper {

    /**
     * Maximum number of teaching-category entries persisted in the
     * comma-separated {@code teaching_categories} column. The frontend
     * enforces a matching cap on the input side; this server-side cap
     * is the storage-layer safety net.
     */
    private static final int MAX_CATEGORIES = 12;

    /** Maximum length of the bio summary surfaced in admin teacher list rows. */
    private static final int BIO_SUMMARY_MAX_LENGTH = 140;

    /** Length of the trailing "{@code ...}" ellipsis appended by {@link #summarize}. */
    private static final int ELLIPSIS_LENGTH = 3;

    /** Delimiter used in the persisted comma-separated category column. */
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
                splitCategories(profile.getTeachingCategories()),
                profile.getTeacherStatus(),
                profile.getRejectionReason(),
                documents.stream().map(this::toDocumentSummary).toList(),
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
                summarize(profile.getBio(), BIO_SUMMARY_MAX_LENGTH),
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

    public AdminStudentListItemResponse toAdminStudentListItem(StudentProfile studentProfile) {
        User user = studentProfile.getUser();
        return new AdminStudentListItemResponse(
                studentProfile.getId(),
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPreferredLanguage(),
                studentProfile.getCreatedAt());
    }

    public PublicTeacherProfileResponse toPublicTeacherProfileResponse(TeacherProfile teacherProfile) {
        return toPublicTeacherProfileResponse(teacherProfile, 0);
    }

    public PublicTeacherProfileResponse toPublicTeacherProfileResponse(TeacherProfile teacherProfile, int courseCount) {
        return new PublicTeacherProfileResponse(
                teacherProfile.getId(),
                teacherProfile.getUser().getDisplayName(),
                teacherProfile.getBio(),
                teacherProfile.getTeacherStatus() == TeacherStatus.APPROVED,
                splitCategories(teacherProfile.getTeachingCategories()),
                courseCount);
    }

    public AdminAuditSummaryResponse toAuditSummary(AuditLog auditLog) {
        return new AdminAuditSummaryResponse(
                auditLog.getId(),
                auditLog.getActionType(),
                auditLog.getTargetType(),
                auditLog.getDescription(),
                auditLog.getCreatedAt());
    }

    /**
     * Trims, deduplicates, strips embedded commas (the column delimiter)
     * and joins a category list into the comma-separated representation
     * persisted in the {@code teaching_categories} column. Capped at
     * {@link #MAX_CATEGORIES} entries; returns {@code null} when the
     * filtered list is empty so the column stores {@code NULL} instead
     * of an empty string.
     */
    public String joinCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        String joined = categories.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .map(value -> value.replace(",", ""))
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(MAX_CATEGORIES)
                .collect(Collectors.joining(CATEGORY_DELIMITER));
        return joined.isEmpty() ? null : joined;
    }

    /**
     * Inverse of {@link #joinCategories}: parses the persisted CSV column
     * back into the per-entry list shape consumed by the API. Returns an
     * immutable empty list when the input is {@code null} or blank so
     * callers can iterate the result unconditionally.
     */
    private List<String> splitCategories(String categories) {
        if (categories == null || categories.isBlank()) {
            return List.of();
        }
        return Arrays.stream(categories.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /**
     * Truncates a multi-line free-text field to {@code maxLength}
     * characters total (including the trailing ellipsis) for display in
     * dense admin list rows. Returns {@code null} for {@code null} or
     * blank input so the response field renders as a missing value
     * rather than an empty string.
     */
    private String summarize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        int contentLength = Math.max(0, maxLength - ELLIPSIS_LENGTH);
        return trimmed.substring(0, contentLength).trim() + "...";
    }
}
