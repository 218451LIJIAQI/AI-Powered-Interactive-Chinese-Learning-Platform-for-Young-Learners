package com.example.aichinese.dto.commerce;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/payout-requests/{id}/reject}
 * — the admin declines a teacher's payout request and supplies a
 * mandatory reason that the teacher sees on
 * {@link TeacherPayoutRequestResponse#decisionReason()}.
 *
 * <p>The other admin payout actions take no body:</p>
 * <ul>
 *   <li>{@code POST .../approve} — flips to
 *       {@link com.example.aichinese.entity.enums.PayoutStatus#APPROVED}
 *       (admin approved; bank transfer pending).</li>
 *   <li>{@code POST .../mark-paid} — terminal transition to
 *       {@link com.example.aichinese.entity.enums.PayoutStatus#PAID}
 *       once the bank transfer settles.</li>
 * </ul>
 *
 * <p>{@link #reason} is {@link NotBlank} so the teacher always
 * receives a justification when their withdrawal is refused.
 * Field-for-field identical to {@link RefundDecisionRequest}; the
 * separate type exists so the refund and payout APIs can evolve
 * independently.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Canonicalisation</h2>
 * <p>The compact constructor strips leading/trailing whitespace from
 * {@link #reason}. Validation still runs on the stripped value,
 * so a body of pure whitespace correctly trips {@link NotBlank}.
 * Without this normalisation a request like {@code "   "} would
 * pass {@link Size} but persist as a useless blank decision note
 * on the teacher's payout-history row.</p>
 *
 * @param reason mandatory admin justification shown verbatim to the
 *               teacher; required, 1–{@value
 *               CommerceDtoConstants#DECISION_REASON_MAX_LENGTH}
 *               chars after stripping
 */
@JsonPropertyOrder({ "reason" })
public record PayoutDecisionRequest(
        @NotBlank(message = "Decision reason is required.")
        @Size(max = CommerceDtoConstants.DECISION_REASON_MAX_LENGTH,
                message = "Decision reason must be {max} characters or fewer.")
        String reason) {

    public PayoutDecisionRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}
