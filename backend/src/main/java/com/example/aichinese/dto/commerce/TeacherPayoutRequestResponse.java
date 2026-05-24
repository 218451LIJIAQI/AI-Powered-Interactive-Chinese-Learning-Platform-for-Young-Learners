package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PayoutStatus;

/**
 * Teacher-facing payout-request row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/teachers/payout-requests} (paginated)</li>
 *   <li>{@code POST /api/v1/teachers/payout-requests} (echoes the
 *       freshly created row)</li>
 * </ul>
 *
 * <p>Drives the teacher's "Earnings &amp; Payouts" page so the
 * teacher can track the admin's decision asynchronously. Once a
 * payout is filed there is no teacher-side cancel/edit path — the
 * admin owns the lifecycle.</p>
 *
 * <h2>{@link #payoutStatus} state machine</h2>
 * <ul>
 *   <li>{@code PENDING} — teacher just filed; awaiting admin.</li>
 *   <li>{@code APPROVED} — admin approved; bank transfer pending
 *       on the platform's accounting side.</li>
 *   <li>{@code PAID} — admin marked the transfer settled;
 *       terminal. The corresponding amount is decremented from
 *       {@link TeacherEarningSummaryResponse#availableBalance()}.</li>
 *   <li>{@code REJECTED} — admin declined;
 *       {@link #decisionReason} carries the explanation. The
 *       earnings stay on the available balance and the teacher can
 *       file again.</li>
 * </ul>
 *
 * <p>{@link #requestedNote} is the teacher's optional note from
 * the create request; {@link #decisionReason} is the admin's
 * required reason on rejection (see {@link PayoutDecisionRequest}).
 * The admin counterpart {@link AdminPayoutRequestResponse} adds the
 * teacher's identity for moderation.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param id             primary key of the underlying
 *                       {@link com.example.aichinese.entity.PayoutRequest}
 * @param amount         requested withdrawal amount in the
 *                       platform base currency, normalised to
 *                       {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                       decimal places
 * @param requestedNote  optional teacher-supplied note from
 *                       {@link PayoutRequestCreateRequest};
 *                       {@code null} when omitted
 * @param decisionReason mandatory admin justification supplied via
 *                       {@link PayoutDecisionRequest}; populated
 *                       only for {@link PayoutStatus#REJECTED},
 *                       otherwise {@code null}
 * @param payoutStatus   current state in the {@link PayoutStatus}
 *                       state machine
 * @param createdAt      timestamp the teacher filed the request;
 *                       always populated
 */
@JsonPropertyOrder({
        "id",
        "amount",
        "requestedNote",
        "decisionReason",
        "payoutStatus",
        "createdAt"
})
public record TeacherPayoutRequestResponse(
        Long id,
        BigDecimal amount,
        String requestedNote,
        String decisionReason,
        PayoutStatus payoutStatus,
        LocalDateTime createdAt) {
}
