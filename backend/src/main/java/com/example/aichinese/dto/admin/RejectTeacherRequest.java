package com.example.aichinese.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rejection payload for
 * {@code POST /api/v1/admin/teachers/{teacherId}/reject}.
 *
 * <p>Unlike {@link ApproveTeacherRequest#note()}, {@link #reason} is
 * <em>required</em> ({@link NotBlank}). The asymmetry is intentional —
 * a rejected teacher needs to know what to fix on resubmission, so the
 * platform refuses to accept a rejection without a remediation hint.
 * The service writes the reason to:</p>
 *
 * <ul>
 *   <li>{@code TeacherProfile.rejectionReason} — surfaced in the
 *       teacher dashboard so the teacher sees the feedback
 *       immediately on next login.</li>
 *   <li>{@code TeacherVerificationReview.reviewNote} — preserved in
 *       the review history for future admin context.</li>
 *   <li>A {@code Notification} of type
 *       {@link com.example.aichinese.entity.enums.NotificationType#TEACHER_APPROVAL}
 *       so the teacher gets an in-app alert.</li>
 * </ul>
 *
 * @param reason explanation surfaced to the teacher; required and
 *               capped at 1000 characters
 */
public record RejectTeacherRequest(
        @NotBlank(message = "Rejection reason is required.")
        @Size(max = 1000, message = "Rejection reason must be 1000 characters or fewer.")
        String reason) {
}
