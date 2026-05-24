package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PaymentStatus;

/**
 * Admin-facing payment record returned by:
 * <ul>
 *   <li>{@code GET /api/v1/admin/payments} (paginated cross-student
 *       list)</li>
 *   <li>{@code GET /api/v1/admin/payments/{paymentId}} (detail)</li>
 * </ul>
 *
 * <p>Drives the admin commerce dashboard's "All payments" screen.
 * Mirrors {@link StudentPaymentRecordResponse} field-for-field plus
 * the buyer's identity ({@link #studentId}, {@link #studentName},
 * {@link #studentEmail}, denormalised from {@code users}) so admins
 * can pivot from a payment row to messaging/refund actions without
 * an additional lookup.</p>
 *
 * <p>{@link #studentName} is the {@code users.display_name} (the
 * historical naming that carries through from
 * {@code TeacherStudentSummaryResponse#fullName}); the column in
 * the underlying entity is {@code display_name}, not
 * {@code full_name}.</p>
 *
 * <p>See {@link StudentPaymentRecordResponse} for the discussion of
 * {@link #paymentStatus} vs {@link #providerStatus}, the
 * {@link #items} snapshot semantics, and the synchronous flow
 * around the Stripe checkout / webhook.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Immutability</h2>
 * <p>The compact constructor defensively wraps {@link #items} with
 * an unmodifiable copy: a receipt is an immutable audit-trail row
 * and admin tooling must not be able to mutate the line-item
 * snapshot after the fact.</p>
 *
 * @param paymentId      primary key of the underlying
 *                       {@link com.example.aichinese.entity.PaymentRecord}
 * @param orderId        primary key of the paired
 *                       {@link com.example.aichinese.entity.Order};
 *                       {@code null} only when the payment row was
 *                       persisted before its order (an internal
 *                       invariant violation worth surfacing to admin)
 * @param orderNumber    human-readable order reference
 *                       (e.g. {@code "ORD-XXXXXXXX-XXXXXXXX"});
 *                       {@code null} when {@link #orderId} is also
 *                       {@code null}
 * @param studentId      primary key of the buyer's
 *                       {@link com.example.aichinese.entity.StudentProfile}
 * @param studentName    buyer's display name from
 *                       {@code users.display_name}; nullable when the
 *                       user account has been pseudonymised
 * @param studentEmail   buyer's email address; nullable for the same
 *                       reason as {@link #studentName}
 * @param providerName   payment-provider identifier
 *                       (e.g. {@code "stripe"}, {@code "FREE"})
 * @param amount         settled amount in {@link #currencyCode};
 *                       always non-negative, normalised to
 *                       {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                       decimal places
 * @param currencyCode   ISO 4217 three-letter uppercase currency
 *                       (matches
 *                       {@link CommerceDtoConstants#CURRENCY_CODE_PATTERN})
 * @param paymentStatus  canonical platform-side status — front-end
 *                       branches UI on this, never on
 *                       {@link #providerStatus}
 * @param paymentMethod  raw method label echoed from the provider
 *                       (e.g. {@code "card"}, {@code "fpx"})
 * @param providerStatus raw status string from the provider for
 *                       support diagnostics
 * @param paidAt         timestamp the payment moved to
 *                       {@link PaymentStatus#PAID}; {@code null}
 *                       for any other status
 * @param createdAt      order-creation timestamp; always populated
 * @param items          immutable historical line-item snapshot of
 *                       what was bought; never {@code null} (an
 *                       empty list represents an order with no
 *                       persisted items)
 */
@JsonPropertyOrder({
        "paymentId",
        "orderId",
        "orderNumber",
        "studentId",
        "studentName",
        "studentEmail",
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
public record AdminPaymentRecordResponse(
        Long paymentId,
        Long orderId,
        String orderNumber,
        Long studentId,
        String studentName,
        String studentEmail,
        String providerName,
        BigDecimal amount,
        String currencyCode,
        PaymentStatus paymentStatus,
        String paymentMethod,
        String providerStatus,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        List<CartItemResponse> items) {

    public AdminPaymentRecordResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
