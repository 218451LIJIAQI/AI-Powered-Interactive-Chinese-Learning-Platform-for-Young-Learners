package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherEarningStatus;

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
 * <p>Canonical {@link TeacherEarningStatus} value (currently
 * {@link TeacherEarningStatus#AVAILABLE} or
 * {@link TeacherEarningStatus#REFUNDED}). Serialised to the wire
 * as the enum constant name ({@code "AVAILABLE"} /
 * {@code "REFUNDED"}) by Jackson's default enum strategy, so the
 * JSON shape is unchanged from a stringly-typed field. The
 * {@link TeacherEarningStatus#REFUNDED} state is set by the
 * cascade triggered when the underlying order is refunded (see
 * {@link StudentRefundRequestResponse}); refunded earnings are
 * excluded from the available balance.</p>
 *
 * <p>{@link #itemTitle} is the snapshot title from the order line
 * item (the same defensive snapshot documented on
 * {@link CartItemResponse#title()}) so the teacher's ledger
 * remains stable even if the underlying lesson is later renamed.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param id               primary key of the underlying
 *                         {@link com.example.aichinese.entity.TeacherEarning}
 * @param orderItemId      primary key of the
 *                         {@link com.example.aichinese.entity.OrderItem}
 *                         that produced this earning
 * @param itemTitle        snapshot title at order-time; stable
 *                         across later content renames
 * @param grossAmount      buyer-paid amount for this line, normalised
 *                         to {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                         decimal places
 * @param commissionAmount platform commission deducted at credit
 *                         time, computed from the rate active when
 *                         the order was paid
 * @param netAmount        {@code grossAmount - commissionAmount},
 *                         normalised; contributes to the teacher's
 *                         available balance when
 *                         {@link #earningStatus} is
 *                         {@code "AVAILABLE"}
 * @param earningStatus    underlying
 *                         {@link TeacherEarningStatus}
 *                         (typically {@link TeacherEarningStatus#AVAILABLE}
 *                         or {@link TeacherEarningStatus#REFUNDED});
 *                         serialised by name on the wire
 * @param createdAt        timestamp the earning was credited
 *                         (matches the payment time); always populated
 */
@JsonPropertyOrder({
        "id",
        "orderItemId",
        "itemTitle",
        "grossAmount",
        "commissionAmount",
        "netAmount",
        "currencyCode",
        "earningStatus",
        "createdAt"
})
public record TeacherEarningResponse(
        Long id,
        Long orderItemId,
        String itemTitle,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        String currencyCode,
        TeacherEarningStatus earningStatus,
        LocalDateTime createdAt) {
}
