package com.example.aichinese.dto.admin;

import jakarta.validation.constraints.Size;

/**
 * Approval payload for
 * {@code POST /api/v1/admin/teachers/{teacherId}/approve}.
 *
 * <p>The endpoint flips the teacher's
 * {@link com.example.aichinese.entity.enums.TeacherStatus} to
 * {@link com.example.aichinese.entity.enums.TeacherStatus#APPROVED}
 * and records a {@code TeacherVerificationReview} for the audit
 * trail. {@link #note} is an optional free-text comment the reviewing
 * admin can attach to the review record (e.g.
 * "Verified TESOL certificate") — it is not surfaced to the teacher,
 * only to other admins inspecting the review history.</p>
 *
 * <p>Approval also flips
 * {@link com.example.aichinese.entity.TeacherProfile#getRejectionReason()}
 * to {@code null} so any prior rejection text no longer haunts the
 * teacher dashboard.</p>
 *
 * @param note optional internal review note (max 1000 chars); may be
 *             {@code null} or blank
 */
public record ApproveTeacherRequest(
        @Size(max = 1000, message = "Approval note must be 1000 characters or fewer.")
        String note) {
}
