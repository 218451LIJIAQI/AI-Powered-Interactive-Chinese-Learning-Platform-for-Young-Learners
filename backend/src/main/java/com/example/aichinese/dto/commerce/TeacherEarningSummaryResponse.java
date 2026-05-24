package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

/**
 * Roll-up of the teacher's lifetime earnings, returned by
 * {@code GET /api/v1/teachers/earnings/summary}.
 *
 * <p>Drives the headline numbers on the teacher's
 * "Earnings &amp; Payouts" dashboard, complementing the per-line
 * {@link TeacherEarningResponse} ledger.</p>
 *
 * <h2>Amount breakdown</h2>
 * <ul>
 *   <li>{@link #grossAmount} — sum of
 *       {@link TeacherEarningResponse#grossAmount()} across all
 *       earnings (refunded and not).</li>
 *   <li>{@link #totalNetAmount} — sum of
 *       {@link TeacherEarningResponse#netAmount()} across all
 *       non-refunded earnings (the lifetime "I earned this much"
 *       headline).</li>
 *   <li>{@link #availableBalance} —
 *       {@code totalNetAmount - already-paid-out - in-flight payouts}.
 *       The amount the teacher can withdraw right now via
 *       {@link PayoutRequestCreateRequest}.</li>
 *   <li>{@link #pendingBalance} — net amount tied up in
 *       still-pending payout requests (filed but not yet paid).
 *       Once the admin marks a payout
 *       {@link com.example.aichinese.entity.enums.PayoutStatus#PAID},
 *       the corresponding amount moves out of pending and out of
 *       the available balance.</li>
 * </ul>
 *
 * <p>Invariant: {@code availableBalance + pendingBalance + sum-of-paid}
 * always equals {@code totalNetAmount}. The teacher cannot file a
 * payout for more than {@link #availableBalance} (see
 * {@link PayoutRequestCreateRequest} validation).</p>
 */
public record TeacherEarningSummaryResponse(
        BigDecimal grossAmount,
        BigDecimal totalNetAmount,
        BigDecimal availableBalance,
        BigDecimal pendingBalance) {
}
