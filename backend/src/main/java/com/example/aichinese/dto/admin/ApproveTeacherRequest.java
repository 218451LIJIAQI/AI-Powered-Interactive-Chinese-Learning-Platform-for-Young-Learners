package com.example.aichinese.dto.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * {@link #note} so {@link Size @Size(max=1000)} is evaluated against
 * the canonical form (a paste with stray leading/trailing whitespace
 * cannot inflate the length past the cap) and so every downstream
 * consumer — the persisted review row, the audit log entry, and the
 * approval email — sees the same trimmed value rather than relying
 * on each consumer to trim independently. {@code null} is preserved
 * so the optionality semantics on the wire format are stable.</p>
 *
 * @param note optional internal review note (max 1000 chars); trimmed
 *             by the canonical constructor; may be {@code null} or
 *             empty after trimming
 */
@JsonPropertyOrder({ "note" })
public record ApproveTeacherRequest(
        @Size(max = 1000, message = "Approval note must be 1000 characters or fewer.")
        String note) {

    /**
     * Defensive canonical constructor: trims surrounding whitespace
     * from {@link #note} so every downstream consumer (persisted
     * review row, audit log, approval email) receives the canonical
     * form regardless of whether they remember to trim independently.
     * {@code null} is preserved so Bean Validation's
     * {@link Size @Size} treats the field as absent rather than empty.
     */
    public ApproveTeacherRequest {
        if (note != null) {
            note = note.trim();
        }
    }
}
