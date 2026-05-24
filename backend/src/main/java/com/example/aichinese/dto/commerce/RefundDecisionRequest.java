package com.example.aichinese.dto.commerce;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/refund-requests/{id}/reject}
 * — the admin declines a student's refund request and supplies a
 * mandatory reason that the student sees on
 * {@link StudentRefundRequestResponse#decisionReason()}.
 *
 * <p>Approval ({@code POST .../approve}) and the terminal mark-paid
 * step ({@code POST .../mark-refunded}) do <em>not</em> consume
 * this DTO — they take an empty body, since approving a refund
 * does not require a customer-facing reason. This DTO is the
 * single-purpose "rejection note" carrier.</p>
 *
 * <p>{@link #reason} is {@link NotBlank} (admin must provide
 * justification when refusing a refund — child-safe consumer
 * platforms shouldn't reject refunds silently). Capped at
 * {@value CommerceDtoConstants#DECISION_REASON_MAX_LENGTH} chars
 * to match the schema column.</p>
 *
 * <p>Field-for-field identical to {@link PayoutDecisionRequest};
 * the separate type exists so the refund and payout APIs can
 * evolve independently.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Canonicalisation</h2>
 * <p>The compact constructor strips leading/trailing whitespace from
 * {@link #reason}. Validation still runs on the stripped value,
 * so a body of pure whitespace correctly trips {@link NotBlank}
 * rather than persisting as a useless blank rejection note on the
 * student's refund row.</p>
 *
 * @param reason mandatory admin justification shown verbatim to the
 *               student; required, 1–{@value
 *               CommerceDtoConstants#DECISION_REASON_MAX_LENGTH}
 *               chars after stripping
 */
@JsonPropertyOrder({ "reason" })
public record RefundDecisionRequest(
        @NotBlank(message = "Decision reason is required.")
        @Size(max = CommerceDtoConstants.DECISION_REASON_MAX_LENGTH,
                message = "Decision reason must be {max} characters or fewer.")
        String reason) {

    public RefundDecisionRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}
