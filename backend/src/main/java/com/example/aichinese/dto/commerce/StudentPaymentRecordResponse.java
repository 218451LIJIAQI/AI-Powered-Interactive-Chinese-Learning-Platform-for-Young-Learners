package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PaymentStatus;

/**
 * Student-facing payment receipt returned by:
 * <ul>
 *   <li>{@code GET /api/v1/students/payments} (paginated list)</li>
 *   <li>{@code GET /api/v1/students/payments/{paymentId}} (detail)</li>
 *   <li>{@code GET /api/v1/payments/status/{paymentId}} (re-verify
 *       against the upstream provider; runs the synchronous status
 *       fetch and reflects the latest state)</li>
 * </ul>
 *
 * <p>Drives the student's "Order history" / "Payment status" pages.
 * Each row corresponds to one
 * {@link com.example.aichinese.entity.PaymentRecord} (and its
 * paired {@link com.example.aichinese.entity.Order}); the
 * embedded {@link #items} carries the historical
 * {@link CartItemResponse} snapshot of what was bought.</p>
 *
 * <h2>{@link #paymentStatus} vs {@link #providerStatus}</h2>
 * <ul>
 *   <li>{@link #paymentStatus} — canonical
 *       {@link PaymentStatus} after the platform has mapped the
 *       provider response (PENDING / PAID / FAILED / REFUNDED / etc.).
 *       This is the field the front-end branches UI on.</li>
 *   <li>{@link #providerStatus} — raw status string from the
 *       provider (e.g. Stripe's {@code "complete"},
 *       {@code "open"}, {@code "expired"}). Surfaced for support
 *       diagnostics, not for direct UI logic.</li>
 * </ul>
 *
 * <p>{@link #paidAt} is set when {@link #paymentStatus} reaches
 * {@link PaymentStatus#PAID}; {@code null} for any other state.
 * {@link #createdAt} is the order-creation time and is always
 * populated.</p>
 *
 * <p>The admin-facing counterpart {@link AdminPaymentRecordResponse}
 * adds the buyer's identity ({@code studentId}, {@code studentName},
 * {@code studentEmail}) for moderation tooling.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Immutability</h2>
 * <p>The compact constructor defensively wraps {@link #items} with
 * an unmodifiable copy: a paid-order receipt is an immutable
 * audit-trail row even though the field is typed as {@link List}.</p>
 *
 * @param paymentId     primary key of the underlying
 *                      {@link com.example.aichinese.entity.PaymentRecord}
 * @param orderId       primary key of the paired
 *                      {@link com.example.aichinese.entity.Order}
 * @param orderNumber   human-readable order reference
 *                      (e.g. {@code "ORD-XXXXXXXX-XXXXXXXX"})
 * @param providerName  payment-provider identifier
 *                      (e.g. {@code "stripe"}, {@code "FREE"})
 * @param amount        order total in {@link #currencyCode}, normalised
 *                      to {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                      decimal places
 * @param currencyCode  ISO 4217 three-letter uppercase currency
 *                      (matches
 *                      {@link CommerceDtoConstants#CURRENCY_CODE_PATTERN})
 * @param paymentStatus canonical platform-side payment status
 * @param paymentMethod payment method captured at success
 *                      (e.g. {@code "card"}, {@code "FREE"});
 *                      nullable until provider confirmation
 * @param providerStatus raw provider status string for diagnostics
 * @param paidAt        timestamp the payment was confirmed;
 *                      {@code null} unless {@link #paymentStatus}
 *                      is {@link PaymentStatus#PAID} (or post-paid
 *                      states like {@link PaymentStatus#REFUNDED})
 * @param createdAt     order-creation timestamp; always populated
 * @param items         immutable line-item snapshot of what was
 *                      purchased; never {@code null}
 */
@JsonPropertyOrder({
        "paymentId",
        "orderId",
        "orderNumber",
        "providerName",
        "amount",
        "currencyCode",
        "paymentStatus",
        "paymentMethod",
        "providerStatus",
        "paidAt",
        "createdAt",
        "items"
})
public record StudentPaymentRecordResponse(
        Long paymentId,
        Long orderId,
        String orderNumber,
        String providerName,
        BigDecimal amount,
        String currencyCode,
        PaymentStatus paymentStatus,
        String paymentMethod,
        String providerStatus,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        List<CartItemResponse> items) {

    public StudentPaymentRecordResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
