package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One per-sale teacher earning row returned by
 * {@code GET /api/v1/teachers/earnings} (paginated, newest first).
 *
 * <p>Drives the teacher's "Earnings ledger" page — the per-line
 * audit trail behind the rolled-up
 * {@link TeacherEarningSummaryResponse}. Each row corresponds to
 * one {@link com.example.aichinese.entity.TeacherEarning} which is
 * itself created when an order line item the teacher authored
 * gets paid.</p>
 *
 * <h2>Amount breakdown</h2>
 * <ul>
 *   <li>{@link #grossAmount} — what the student paid for this line
 *       item (matches the corresponding
 *       {@link CartItemResponse#subtotal()}).</li>
 *   <li>{@link #commissionAmount} — platform commission deducted
 *       at the time of crediting; computed as
 *       {@code grossAmount * commissionRate}. Frozen at the rate
 *       active at payment time — see
 *       {@link CommissionSettingRequest} for why the rate change is
 *       prospective only.</li>
 *   <li>{@link #netAmount} —
 *       {@code grossAmount - commissionAmount}, the amount that
 *       contributes to {@link TeacherEarningSummaryResponse#totalNetAmount()}.</li>
 * </ul>
 *
 * <h2>{@link #earningStatus}</h2>
 * <p>String form of
 * {@link com.example.aichinese.entity.enums.TeacherEarningStatus}
 * (currently {@code "AVAILABLE"} or {@code "REFUNDED"}). The
 * {@code REFUNDED} state is set by the cascade triggered when the
 * underlying order is refunded (see
 * {@link StudentRefundRequestResponse}); refunded earnings are
 * excluded from the available balance.</p>
 *
 * <p>{@link #itemTitle} is the snapshot title from the order line
 * item (the same defensive snapshot documented on
 * {@link CartItemResponse#title()}) so the teacher's ledger
 * remains stable even if the underlying lesson is later renamed.</p>
 */
public record TeacherEarningResponse(
        Long id,
        Long orderItemId,
        String itemTitle,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        String earningStatus,
        LocalDateTime createdAt) {
}
