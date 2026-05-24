package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * {@link Digits}{@code(integer = }{@value
 * CommerceDtoConstants#MONEY_INTEGER_DIGITS}{@code , fraction = }
 * {@value CommerceDtoConstants#MONEY_FRACTION_DIGITS}{@code )}
 * matches the {@code DECIMAL(10,2)} schema column. Currency is
 * implicit — all payouts use the platform's base currency
 * configured in {@code AdminPlatformSettings}.</p>
 *
 * <h2>{@link #requestedNote}</h2>
 * <p>Optional free-text from the teacher (e.g. "Withdrawing for
 * end-of-month rent"). Shown to admins during review; capped at
 * {@value CommerceDtoConstants#PAYOUT_NOTE_MAX_LENGTH} chars and
 * stripped of leading/trailing whitespace by the compact
 * constructor (a blank-after-stripping note is normalised to
 * {@code null} so the persisted column doesn't carry useless
 * whitespace).</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param amount        withdrawal amount in the platform base
 *                      currency; required, {@code >= 0.01}, fits in
 *                      {@code DECIMAL(10,2)}
 * @param requestedNote optional teacher-supplied note; max {@value
 *                      CommerceDtoConstants#PAYOUT_NOTE_MAX_LENGTH}
 *                      chars after stripping; {@code null} when
 *                      omitted or blank
 */
@JsonPropertyOrder({ "amount", "requestedNote" })
public record PayoutRequestCreateRequest(
        @NotNull(message = "Payout amount is required.")
        @DecimalMin(value = "0.01", inclusive = true,
                message = "Payout amount must be at least {value}.")
        @Digits(integer = CommerceDtoConstants.MONEY_INTEGER_DIGITS,
                fraction = CommerceDtoConstants.MONEY_FRACTION_DIGITS,
                message = "Payout amount must fit within {integer} integer digits and {fraction} decimal places.")
        BigDecimal amount,
        @Size(max = CommerceDtoConstants.PAYOUT_NOTE_MAX_LENGTH,
                message = "Payout note must be {max} characters or fewer.")
        String requestedNote) {

    public PayoutRequestCreateRequest {
        if (requestedNote != null) {
            requestedNote = requestedNote.strip();
            if (requestedNote.isEmpty()) {
                requestedNote = null;
            }
        }
    }
}
