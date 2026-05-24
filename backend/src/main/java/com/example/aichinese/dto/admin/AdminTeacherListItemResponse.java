package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;

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
 * @param teacherId                 {@code TeacherProfile} identifier
 * @param userId                    {@code User} identifier
 * @param fullName                  display name on the user record
 * @param email                     lower-cased login email
 * @param teacherStatus             current verification state
 * @param bioSummary                truncated bio preview; may be
 *                                  {@code null} when bio is empty
 * @param verificationDocumentCount number of documents in the latest
 *                                  submission round
 * @param latestReviewDate          when the most recent admin review
 *                                  was decided; {@code null} when the
 *                                  teacher has never been reviewed
 * @param latestSubmissionTime      when the most recent verification
 *                                  document was uploaded; {@code null}
 *                                  when the teacher has not submitted
 *                                  any documents
 * @param createdAt                 when the teacher account was created
 */
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
}
