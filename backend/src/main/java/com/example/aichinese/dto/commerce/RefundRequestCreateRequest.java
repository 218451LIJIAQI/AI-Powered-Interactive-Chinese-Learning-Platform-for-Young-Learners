package com.example.aichinese.dto.commerce;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * <p>{@link #reason} is mandatory and capped at {@value
 * CommerceDtoConstants#REFUND_REASON_MAX_LENGTH} chars — the
 * student's free-text justification shown to the admin reviewer.
 * The service additionally re-trims the value before persistence
 * for belt-and-braces defence.</p>
 *
 * <p>The refund <em>amount</em> is not on this DTO; the platform
 * always refunds the full order total. Partial refunds are not
 * supported by the current workflow.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Canonicalisation</h2>
 * <p>The compact constructor strips leading/trailing whitespace from
 * {@link #reason}. Validation then runs on the stripped value, so
 * a payload of pure whitespace correctly trips {@link NotBlank}
 * rather than degrading into a useless blank persisted row.</p>
 *
 * @param orderId primary key of the order being refunded; required
 *                and must be positive
 * @param reason  required student justification; 1–{@value
 *                CommerceDtoConstants#REFUND_REASON_MAX_LENGTH}
 *                chars after stripping
 */
@JsonPropertyOrder({ "orderId", "reason" })
public record RefundRequestCreateRequest(
        @NotNull(message = "Order id is required.")
        @Positive(message = "Order id must be positive.")
        Long orderId,
        @NotBlank(message = "Refund reason is required.")
        @Size(max = CommerceDtoConstants.REFUND_REASON_MAX_LENGTH,
                message = "Refund reason must be {max} characters or fewer.")
        String reason) {

    public RefundRequestCreateRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}
