package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
 */
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
}
