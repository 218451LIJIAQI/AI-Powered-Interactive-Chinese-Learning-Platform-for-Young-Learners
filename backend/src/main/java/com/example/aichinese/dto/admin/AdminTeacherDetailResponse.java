package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.dto.teacher.TeacherVerificationDocumentSummaryResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationReviewSummaryResponse;
import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Aggregated teacher detail payload returned by the admin
 * {@code GET /api/v1/admin/teachers/{teacherId}} endpoint.
 *
 * <p>Drives the teacher-verification review screen, where an admin
 * weighs the uploaded documents, the prior-decision history, and any
 * recent audit log activity before clicking "Approve" or "Reject".
 * The payload bundles all four data sources into one DTO so the UI
 * renders without follow-up requests:</p>
 *
 * <ul>
 *   <li><b>Identity</b>: {@link #teacherId}, {@link #userId},
 *       {@link #fullName}, {@link #email}.</li>
 *   <li><b>Public profile</b>: {@link #bio}, {@link #shortIntroduction},
 *       {@link #teachingCategories}.</li>
 *   <li><b>Verification state</b>: {@link #teacherStatus},
 *       {@link #rejectionReason}, {@link #verifiedAt}.</li>
 *   <li><b>Evidence</b>: {@link #verificationDocuments} (the latest
 *       round of submitted credentials).</li>
 *   <li><b>History</b>: {@link #verificationReviewHistory} (every prior
 *       admin decision, newest first) and {@link #recentAuditLogs}
 *       (cross-cutting actions such as login lockouts that may inform
 *       the review).</li>
 * </ul>
 *
 * <p>{@link #shortIntroduction} is a derived field (typically the
 * first sentence of {@link #bio}) emitted by the mapper for use as a
 * one-line label in lists.</p>
 *
 * @param teacherId                 {@code TeacherProfile} identifier
 * @param userId                    {@code User} identifier
 * @param fullName                  display name on the user record
 * @param email                     lower-cased login email
 * @param bio                       full public-profile blurb
 * @param shortIntroduction         truncated single-line preview of
 *                                  {@link #bio}; may be {@code null}
 *                                  when the bio is empty
 * @param teachingCategories        free-form labels chosen by the
 *                                  teacher; never {@code null} (empty
 *                                  list when none configured)
 * @param teacherStatus             current verification state
 * @param rejectionReason           populated when the latest review
 *                                  was {@code REJECTED}
 * @param verifiedAt                timestamp of the most recent
 *                                  approval, or {@code null} when
 *                                  never approved
 * @param verificationDocuments     latest-round supporting documents
 * @param verificationReviewHistory all admin decisions to date, newest
 *                                  first
 * @param recentAuditLogs           recent cross-cutting audit rows
 *                                  related to the teacher
 * @param createdAt                 when the teacher account was created
 * @param updatedAt                 when the teacher profile was last
 *                                  modified
 */
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
