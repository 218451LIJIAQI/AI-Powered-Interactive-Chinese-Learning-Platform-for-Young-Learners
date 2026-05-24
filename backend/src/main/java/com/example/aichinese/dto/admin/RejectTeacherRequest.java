package com.example.aichinese.dto.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *   <li>The teacher rejection email body — sent via
 *       {@code emailService.safeSendTeacherRejectionNotice}.</li>
 *   <li>A {@code Notification} of type
 *       {@link com.example.aichinese.entity.enums.NotificationType#TEACHER_APPROVAL}
 *       so the teacher gets an in-app alert.</li>
 * </ul>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * {@link #reason} so:</p>
 * <ul>
 *   <li>{@link Size @Size(max=1000)} is evaluated against the
 *       canonical form (a paste with stray leading/trailing whitespace
 *       cannot inflate the length past the cap).</li>
 *   <li>{@link NotBlank @NotBlank} rejects whitespace-only input
 *       consistently — without DTO-level trim, a request body of
 *       {@code "reason": "   "} would slip past
 *       {@code @NotBlank.isValid("   ")} (which only checks for
 *       non-{@code null} non-empty after stripping) before reaching
 *       the service's redundant guard.</li>
 *   <li>Every downstream consumer (profile field, review row, email
 *       body, notification) receives the same canonical string,
 *       eliminating the previous pattern of {@code reason.trim()}
 *       being repeated at every persistence call site.</li>
 * </ul>
 * <p>{@code null} is preserved so {@link NotBlank @NotBlank} renders
 * the user-facing "Rejection reason is required." message rather than
 * a low-level {@link NullPointerException}.</p>
 *
 * @param reason explanation surfaced to the teacher; trimmed by the
 *               canonical constructor; required and capped at
 *               1000 characters
 */
@JsonPropertyOrder({ "reason" })
public record RejectTeacherRequest(
        @NotBlank(message = "Rejection reason is required.")
        @Size(max = 1000, message = "Rejection reason must be 1000 characters or fewer.")
        String reason) {

    /**
     * Defensive canonical constructor: trims surrounding whitespace
     * from {@link #reason} so every downstream consumer (profile
     * rejection-reason column, review row, email body, in-app
     * notification) receives the canonical form. {@code null} is
     * preserved for {@link NotBlank @NotBlank} to render the friendly
     * user-facing message rather than a low-level
     * {@link NullPointerException}.
     */
    public RejectTeacherRequest {
        if (reason != null) {
            reason = reason.trim();
        }
    }
}
