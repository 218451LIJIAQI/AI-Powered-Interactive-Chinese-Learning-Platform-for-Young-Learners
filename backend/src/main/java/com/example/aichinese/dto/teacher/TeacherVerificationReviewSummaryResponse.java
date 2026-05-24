package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * name only. {@code reviewerName} is nullable when the reviewer user
 * row has been deleted (orphaned audit trail).</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the three fields
 * the mapper guarantees from any persisted review row
 * ({@link #id}, {@link #decisionStatus}, {@link #reviewedAt}).
 * {@link #note} and {@link #reviewerName} remain nullable per the
 * documented contextual rules.</p>
 *
 * @param id              review row id; never {@code null}
 * @param decisionStatus  the {@link TeacherStatus} that this review
 *                        moved the teacher into
 *                        ({@link TeacherStatus#APPROVED} or
 *                        {@link TeacherStatus#REJECTED}); never
 *                        {@code null}
 * @param note            optional free-text rationale supplied by the
 *                        admin reviewer; nullable
 * @param reviewerName    display name of the admin who issued this
 *                        decision; denormalised from
 *                        {@code users.full_name}; nullable when the
 *                        reviewer user row is missing
 * @param reviewedAt      timestamp the decision was recorded; never
 *                        {@code null}
 */
@JsonPropertyOrder({ "id", "decisionStatus", "note", "reviewerName", "reviewedAt" })
public record TeacherVerificationReviewSummaryResponse(
        Long id,
        TeacherStatus decisionStatus,
        String note,
        String reviewerName,
        LocalDateTime reviewedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the three
     * fields the mapper guarantees from any persisted review row so a
     * malformed decision row cannot reach the wire format and confuse
     * the dashboard verification banner.
     */
    public TeacherVerificationReviewSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(decisionStatus, "decisionStatus must not be null");
        Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
    }
}
