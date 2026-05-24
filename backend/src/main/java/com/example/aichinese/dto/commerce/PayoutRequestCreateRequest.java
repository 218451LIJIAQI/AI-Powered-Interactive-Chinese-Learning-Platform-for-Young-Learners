package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/payout-requests} — an
 * approved teacher requests withdrawal of part (or all) of their
 * available earnings balance.
 *
 * <p>The service ({@code PayoutService.createPayoutRequest})
 * validates that:</p>
 * <ul>
 *   <li>The caller has an
 *       {@link com.example.aichinese.entity.enums.TeacherStatus#APPROVED}
 *       teacher profile (otherwise 403).</li>
 *   <li>{@link #amount} is {@code <=} the teacher's
 *       {@link TeacherEarningSummaryResponse#availableBalance()} —
 *       teachers cannot withdraw what has not been credited yet.</li>
 *   <li>No earlier payout request is still
 *       {@link com.example.aichinese.entity.enums.PayoutStatus#PENDING}
 *       (one active payout request per teacher).</li>
 * </ul>
 *
 * <p>The created
 * {@link com.example.aichinese.entity.PayoutRequest} starts in
 * {@link com.example.aichinese.entity.enums.PayoutStatus#PENDING};
 * admins triage it through
 * {@link AdminPayoutRequestResponse}.</p>
 *
 * <h2>{@link #amount}</h2>
 * <p>{@link DecimalMin} {@code 0.01} excludes zero-amount requests.
 * {@link Digits}{@code(integer = 8, fraction = 2)} matches the
 * {@code DECIMAL(10,2)} schema column. Currency is implicit — all
 * payouts use the platform's base currency configured in
 * {@code AdminPlatformSettings}.</p>
 *
 * <h2>{@link #requestedNote}</h2>
 * <p>Optional free-text from the teacher (e.g. "Withdrawing for
 * end-of-month rent"). Shown to admins during review; capped at
 * 1000 chars and sanitized before persistence.</p>
 */
public record PayoutRequestCreateRequest(
        @NotNull(message = "Payout amount is required.")
        @DecimalMin(value = "0.01", inclusive = true, message = "Payout amount must be greater than 0.")
        @Digits(integer = 8, fraction = 2, message = "Payout amount must fit within 8 integer digits and 2 decimal places.")
        BigDecimal amount,
        @Size(max = 1000, message = "Payout note must be 1000 characters or fewer.")
        String requestedNote) {
}
