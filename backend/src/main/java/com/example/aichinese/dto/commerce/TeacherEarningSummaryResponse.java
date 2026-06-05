package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Roll-up of the teacher's lifetime earnings, returned by
 * {@code GET /api/v1/teachers/earnings/summary}.
 *
 * <p>{@code grossAmount} includes every earning, {@code totalNetAmount}
 * excludes refunded earnings, {@code availableBalance} is withdrawable now,
 * and {@code pendingBalance} is locked in submitted payout requests.</p>
 *
 * @param grossAmount      sum of all earnings' gross (buyer-paid)
 *                         amounts, including refunded earnings
 * @param totalNetAmount   sum of net amounts across all
 *                         non-refunded earnings
 * @param availableBalance withdrawable balance =
 *                         {@code totalNetAmount - already-paid-out
 *                         - in-flight payouts}
 * @param pendingBalance   net amount currently locked in pending
 *                         payout requests
 */
@JsonPropertyOrder({
        "grossAmount",
        "totalNetAmount",
        "availableBalance",
        "pendingBalance",
        "currencyCode"
})
public record TeacherEarningSummaryResponse(
        BigDecimal grossAmount,
        BigDecimal totalNetAmount,
        BigDecimal availableBalance,
        BigDecimal pendingBalance,
        String currencyCode) {
}
