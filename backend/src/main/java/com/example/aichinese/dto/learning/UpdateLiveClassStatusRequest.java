package com.example.aichinese.dto.learning;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for
 * {@code PATCH /api/v1/teachers/live-classes/{id}/status} — the
 * teacher transitions a live class through its lifecycle without
 * re-supplying the full record.
 *
 * <h2>{@link #status} state machine</h2>
 * <p>The string is parsed into
 * {@link com.example.aichinese.entity.enums.LiveClassStatus} (case-
 * insensitive). Allowed transitions are validated by
 * {@code LiveLearningAccessService.validateLiveClassStatusTransition}:</p>
 *
 * <pre>
 *      SCHEDULED ──▶ ONGOING ──▶ COMPLETED   (terminal)
 *      SCHEDULED ──▶ CANCELLED              (terminal)
 *      SCHEDULED ◀─▶ RESCHEDULED
 * </pre>
 *
 * <p>{@code COMPLETED} and {@code CANCELLED} are terminal — once
 * either is reached, the live class is locked from further edits
 * (see {@code LiveClassService.ensureLiveClassEditable}, which
 * applies to {@link UpdateLiveClassRequest} but not to this
 * endpoint).</p>
 *
 * <p>Cancelling fans out a notification to every enrolled
 * student.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * {@link #status} so Bean Validation sees the canonical form
 * (a paste with stray whitespace cannot sneak past
 * {@link NotBlank @NotBlank} or the length cap) and so the enum
 * parser sees the canonical token rather than failing
 * {@code "  SCHEDULED ".equalsIgnoreCase("SCHEDULED")}-style
 * comparisons.</p>
 *
 * @param status enum-name string from
 *               {@link com.example.aichinese.entity.enums.LiveClassStatus}
 *               (case-insensitive); required, 1–30 chars after trim
 */
@JsonPropertyOrder({ "status" })
public record UpdateLiveClassStatusRequest(
        @NotBlank(message = "Status is required.")
        @Size(max = 30, message = "Status must be 30 characters or fewer.")
        String status) {

    /**
     * Defensive canonical constructor: trims {@link #status} so
     * Bean Validation evaluates the canonical form and the enum
     * parser sees the canonical token. {@code null} input is
     * preserved so {@link NotBlank @NotBlank} fires correctly.
     */
    public UpdateLiveClassStatusRequest {
        status = status == null ? null : status.trim();
    }
}
