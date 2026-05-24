package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Single row in the admin "all teachers" list.
 *
 * <p>Designed for fast at-a-glance triage of the verification queue.
 * Admins typically sort by
 * {@link com.example.aichinese.entity.enums.TeacherStatus#PENDING}
 * status first, then by {@link #latestSubmissionTime} so the oldest
 * pending request surfaces at the top.</p>
 *
 * <p>{@link #bioSummary} is a truncated single-line preview of the
 * teacher's full bio (computed by the mapper) suitable for the table
 * column without triggering the row to break visually. The full bio
 * is only loaded when an admin opens
 * {@link AdminTeacherDetailResponse}.</p>
 *
 * <p>{@link #verificationDocumentCount} is the count of <em>current</em>
 * documents (where {@code currentVersion = true}) so the UI can show
 * "3 documents" badges without joining the document table at render
 * time.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #teacherId}, {@link #userId}, {@link #fullName},
 *       {@link #email}, {@link #teacherStatus} and {@link #createdAt}
 *       are non-{@code null} — these are the columns the admin
 *       table renders unconditionally.</li>
 *   <li>{@link #verificationDocumentCount} is clamped to
 *       non-negative; an upstream count-query that produced a negative
 *       value is silently normalised so the wire format never exposes
 *       impossible counts to the frontend.</li>
 *   <li>{@link #bioSummary}, {@link #latestReviewDate} and
 *       {@link #latestSubmissionTime} may be {@code null} per the
 *       contextual rules above.</li>
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
 * @param teacherStatus             current verification state;
 *                                  never {@code null}
 * @param bioSummary                truncated bio preview; may be
 *                                  {@code null} when bio is empty
 * @param verificationDocumentCount number of documents in the latest
 *                                  submission round; always
 *                                  non-negative
 * @param latestReviewDate          when the most recent admin review
 *                                  was decided; {@code null} when the
 *                                  teacher has never been reviewed
 * @param latestSubmissionTime      when the most recent verification
 *                                  document was uploaded; {@code null}
 *                                  when the teacher has not submitted
 *                                  any documents
 * @param createdAt                 when the teacher account was created;
 *                                  never {@code null}
 */
@JsonPropertyOrder({
        "teacherId",
        "userId",
        "fullName",
        "email",
        "teacherStatus",
        "bioSummary",
        "verificationDocumentCount",
        "latestReviewDate",
        "latestSubmissionTime",
        "createdAt"
})
public record AdminTeacherListItemResponse(
        Long teacherId,
        Long userId,
        String fullName,
        String email,
        TeacherStatus teacherStatus,
        String bioSummary,
        long verificationDocumentCount,
        LocalDateTime latestReviewDate,
        LocalDateTime latestSubmissionTime,
        LocalDateTime createdAt) {

    /**
     * Defensive canonical constructor: enforces non-null on every
     * field the admin teachers table renders unconditionally, and
     * clamps {@link #verificationDocumentCount} to be non-negative so
     * a malformed upstream count-query cannot surface an impossible
     * number to the frontend. The contextual nullable fields
     * ({@code bioSummary}, {@code latestReviewDate},
     * {@code latestSubmissionTime}) are intentionally permitted to
     * be {@code null} per the invariants documented above.
     */
    public AdminTeacherListItemResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        verificationDocumentCount = Math.max(verificationDocumentCount, 0L);
    }
}
