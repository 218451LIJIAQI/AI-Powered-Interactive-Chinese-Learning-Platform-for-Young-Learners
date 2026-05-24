package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 */
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
