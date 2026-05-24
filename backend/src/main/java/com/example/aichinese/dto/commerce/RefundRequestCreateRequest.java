package com.example.aichinese.dto.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/students/refund-requests} — a
 * student opens a refund request against one of their previous
 * paid orders.
 *
 * <p>The service ({@code RefundService.createRefundRequest})
 * validates that:</p>
 * <ul>
 *   <li>The {@link #orderId} belongs to the calling student
 *       (otherwise 404).</li>
 *   <li>The order's payment is in
 *       {@link com.example.aichinese.entity.enums.PaymentStatus#PAID}
 *       (you cannot refund what has not been paid for).</li>
 *   <li>No earlier refund request is still open / approved for the
 *       same order (one active refund per order).</li>
 * </ul>
 *
 * <p>The created
 * {@link com.example.aichinese.entity.RefundRequest} starts in
 * {@link com.example.aichinese.entity.enums.RefundStatus#PENDING}
 * and the admin-facing list ({@link AdminRefundRequestResponse})
 * picks it up. The student-facing
 * {@link StudentRefundRequestResponse} echoes the freshly created
 * row on this endpoint.</p>
 *
 * <p>{@link #reason} is mandatory and capped at 1000 chars — it is
 * the student's free-text justification shown to the admin
 * reviewer. The service sanitizes the input before persistence.</p>
 *
 * <p>The refund <em>amount</em> is not on this DTO; the platform
 * always refunds the full order total. Partial refunds are not
 * supported by the current workflow.</p>
 */
public record RefundRequestCreateRequest(
        @NotNull(message = "Order id is required.")
        @Positive(message = "Order id must be positive.")
        Long orderId,
        @NotBlank(message = "Refund reason is required.")
        @Size(max = 1000, message = "Refund reason must be 1000 characters or fewer.")
        String reason) {
}
