package com.example.aichinese.dto.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/refund-requests/{id}/reject}
 * — the admin declines a student's refund request and supplies a
 * mandatory reason that the student sees on
 * {@link StudentRefundRequestResponse#decisionReason()}.
 *
 * <p>Approval ({@code POST .../approve}) and the terminal mark-paid
 * step ({@code POST .../mark-refunded}) do <em>not</em> consume
 * this DTO — they take an empty body, since approving a refund
 * does not require a customer-facing reason. This DTO is the
 * single-purpose "rejection note" carrier.</p>
 *
 * <p>{@link #reason} is {@link NotBlank} (admin must provide
 * justification when refusing a refund — child-safe consumer
 * platforms shouldn't reject refunds silently). Capped at 1000
 * chars to match the schema column.</p>
 *
 * <p>Field-for-field identical to {@link PayoutDecisionRequest};
 * the separate type exists so the refund and payout APIs can
 * evolve independently.</p>
 */
public record RefundDecisionRequest(
        @NotBlank(message = "Decision reason is required.")
        @Size(max = 1000, message = "Decision reason must be 1000 characters or fewer.")
        String reason) {
}
