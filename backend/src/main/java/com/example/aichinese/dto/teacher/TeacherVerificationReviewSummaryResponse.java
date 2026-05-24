package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Single admin-decision row from {@code teacher_verification_reviews},
 * surfaced as
 * {@link TeacherVerificationStatusResponse#latestReviewResult()} on
 * the teacher-facing verification widget.
 *
 * <p>Each review represents one cycle of the verification state
 * machine — see {@link TeacherStatus} for the full transition graph
 * ({@code PENDING ↔ APPROVED} or {@code REJECTED}, with
 * {@code REJECTED → PENDING} via teacher resubmission). This DTO
 * shows only the <em>latest</em> review; historical reviews are
 * retained server-side for audit but not exposed to the teacher.</p>
 *
 * <p>{@link #reviewerName} is denormalised from the reviewer's
 * {@code users.full_name} so the front-end can show "Reviewed by
 * Dr. Chen" without an additional user-lookup round trip. The
 * underlying reviewer user id is intentionally omitted from this
 * teacher-facing payload — staff identity is reduced to the display
 * name only.</p>
 *
 * @param id              review row id
 * @param decisionStatus  the {@link TeacherStatus} that this review
 *                        moved the teacher into
 *                        ({@link TeacherStatus#APPROVED} or
 *                        {@link TeacherStatus#REJECTED})
 * @param note            optional free-text rationale supplied by the
 *                        admin reviewer
 * @param reviewerName    display name of the admin who issued this
 *                        decision; denormalised from
 *                        {@code users.full_name}
 * @param reviewedAt      timestamp the decision was recorded
 */
public record TeacherVerificationReviewSummaryResponse(
        Long id,
        TeacherStatus decisionStatus,
        String note,
        String reviewerName,
        LocalDateTime reviewedAt) {
}
