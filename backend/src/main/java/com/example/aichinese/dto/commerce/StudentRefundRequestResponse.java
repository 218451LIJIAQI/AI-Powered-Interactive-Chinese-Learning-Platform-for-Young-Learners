package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.RefundStatus;

/**
 * Student-facing refund-request row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/students/refund-requests} (paginated)</li>
 *   <li>{@code POST /api/v1/students/refund-requests} (echoes the
 *       freshly created row)</li>
 * </ul>
 *
 * <p>Drives the "My refund requests" page so the student can track
 * the admin's decision asynchronously. Carries the request's full
 * lifecycle in one row — once a refund is filed there is no
 * student-side update path, the admin owns the lifecycle.</p>
 *
 * <h2>{@link #refundStatus} state machine</h2>
 * <p>See {@link RefundStatus} for the full transitions:</p>
 * <ul>
 *   <li>{@code PENDING} — student just filed; awaiting admin.</li>
 *   <li>{@code APPROVED} — admin approved but the money has not
 *       yet been moved (Stripe-side refund pending).</li>
 *   <li>{@code REFUNDED} — admin has marked the refund settled;
 *       money returned to the student's payment method. Triggers
 *       the
 *       {@link com.example.aichinese.entity.enums.TeacherEarningStatus#REFUNDED}
 *       flip on every related teacher earning row to claw the
 *       commission back.</li>
 *   <li>{@code REJECTED} — admin declined; {@link #decisionReason}
 *       carries the explanation.</li>
 * </ul>
 *
 * <p>{@link #amount} is the order total the platform will refund
 * (full-order policy — no partial refunds today).
 * {@link #decisionReason} is populated only for {@code REJECTED};
 * empty for {@code PENDING}/{@code APPROVED}/{@code REFUNDED}.</p>
 *
 * <p>The admin-facing counterpart {@link AdminRefundRequestResponse}
 * adds buyer identity for moderation.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param id             primary key of the underlying
 *                       {@link com.example.aichinese.entity.RefundRequest}
 * @param orderId        primary key of the refunded
 *                       {@link com.example.aichinese.entity.Order}
 * @param orderNumber    human-readable order reference
 *                       (e.g. {@code "ORD-XXXXXXXX-XXXXXXXX"})
 * @param amount         refund amount in the order's currency
 *                       (always equals the order total), normalised
 *                       to {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                       decimal places
 * @param reason         student-supplied rationale (max {@value
 *                       CommerceDtoConstants#REFUND_REASON_MAX_LENGTH}
 *                       chars) from {@link RefundRequestCreateRequest}
 * @param decisionReason mandatory rejection note supplied by the
 *                       admin via {@link RefundDecisionRequest};
 *                       populated only for
 *                       {@link RefundStatus#REJECTED}, otherwise
 *                       {@code null}
 * @param refundStatus   current state in the {@link RefundStatus}
 *                       state machine
 * @param createdAt      timestamp the student filed the request;
 *                       always populated
 */
@JsonPropertyOrder({
        "id",
        "orderId",
        "orderNumber",
        "amount",
        "reason",
        "decisionReason",
        "refundStatus",
        "createdAt"
})
public record StudentRefundRequestResponse(
        Long id,
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        String reason,
        String decisionReason,
        RefundStatus refundStatus,
        LocalDateTime createdAt) {
}
