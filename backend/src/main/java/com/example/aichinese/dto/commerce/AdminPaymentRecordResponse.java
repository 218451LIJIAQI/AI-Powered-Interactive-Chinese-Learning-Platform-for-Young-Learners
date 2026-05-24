package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
 */
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
}
