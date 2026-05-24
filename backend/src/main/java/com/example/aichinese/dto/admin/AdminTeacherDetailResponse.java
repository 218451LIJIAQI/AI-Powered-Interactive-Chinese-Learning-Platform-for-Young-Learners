package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #teacherId}, {@link #userId}, {@link #fullName},
 *       {@link #email}, {@link #teacherStatus}, {@link #createdAt}
 *       and {@link #updatedAt} are non-{@code null} — they are the
 *       review-screen header fields the UI renders unconditionally.</li>
 *   <li>{@link #teachingCategories}, {@link #verificationDocuments},
 *       {@link #verificationReviewHistory} and {@link #recentAuditLogs}
 *       are non-{@code null} (empty lists are used when no rows exist)
 *       and defensively copied so external mutation cannot leak into
 *       the already-serialised payload.</li>
 *   <li>{@link #bio}, {@link #shortIntroduction},
 *       {@link #rejectionReason} and {@link #verifiedAt} may be
 *       {@code null} per the contextual rules above.</li>
 * </ul>
 *
 * @param teacherId                 {@code TeacherProfile} identifier;
 *                                  never {@code null}
 * @param userId                    {@code User} identifier;
 *                                  never {@code null}
 * @param fullName                  display name on the user record;
 *                                  never {@code null}
 * @param email                     lower-cased login email;
 *                                  never {@code null}
 * @param bio                       full public-profile blurb;
 *                                  may be {@code null}
 * @param shortIntroduction         truncated single-line preview of
 *                                  {@link #bio}; may be {@code null}
 *                                  when the bio is empty
 * @param teachingCategories        free-form labels chosen by the
 *                                  teacher; never {@code null} (empty
 *                                  list when none configured)
 * @param teacherStatus             current verification state;
 *                                  never {@code null}
 * @param rejectionReason           populated when the latest review
 *                                  was {@link TeacherStatus#REJECTED};
 *                                  may be {@code null}
 * @param verifiedAt                timestamp of the most recent
 *                                  approval, or {@code null} when
 *                                  never approved
 * @param verificationDocuments     latest-round supporting documents;
 *                                  never {@code null}
 * @param verificationReviewHistory all admin decisions to date,
 *                                  newest first; never {@code null}
 * @param recentAuditLogs           recent cross-cutting audit rows
 *                                  related to the teacher;
 *                                  never {@code null}
 * @param createdAt                 when the teacher account was created;
 *                                  never {@code null}
 * @param updatedAt                 when the teacher profile was last
 *                                  modified; never {@code null}
 */
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

    /**
     * Defensive canonical constructor: enforces non-null on the review
     * screen's header fields, defensively copies every embedded list
     * (with {@code null} → {@link List#of()} normalisation) so external
     * mutation cannot leak into the already-serialised payload, and
     * leaves the contextual nullable fields ({@code bio},
     * {@code shortIntroduction}, {@code rejectionReason},
     * {@code verifiedAt}) untouched.
     */
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
