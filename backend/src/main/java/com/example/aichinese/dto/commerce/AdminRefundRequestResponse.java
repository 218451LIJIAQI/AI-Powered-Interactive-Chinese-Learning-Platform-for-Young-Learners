package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.RefundStatus;

/**
 * Admin-facing refund-request row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/admin/refund-requests} (paginated
 *       cross-student list)</li>
 *   <li>{@code POST /api/v1/admin/refund-requests/{id}/approve}
 *       (no body, echoes the updated row in
 *       {@link RefundStatus#APPROVED})</li>
 *   <li>{@code POST /api/v1/admin/refund-requests/{id}/reject}
 *       — see {@link RefundDecisionRequest}; flips status to
 *       {@link RefundStatus#REJECTED}</li>
 *   <li>{@code POST /api/v1/admin/refund-requests/{id}/mark-refunded}
 *       — terminal transition to {@link RefundStatus#REFUNDED}
 *       once the platform-side refund settles</li>
 * </ul>
 *
 * <p>Drives the admin commerce dashboard's refund triage screen.
 * Mirrors {@link StudentRefundRequestResponse} field-for-field plus
 * the buyer's identity ({@link #studentId}, {@link #studentName},
 * {@link #studentEmail}) for direct messaging during moderation.</p>
 *
 * <p>See {@link StudentRefundRequestResponse} for the
 * {@link RefundStatus} state machine and the cascading
 * {@link com.example.aichinese.entity.enums.TeacherEarningStatus#REFUNDED}
 * earnings claw-back semantics.</p>
 */
public record AdminRefundRequestResponse(
        Long id,
        Long orderId,
        String orderNumber,
        Long studentId,
        String studentName,
        String studentEmail,
        BigDecimal amount,
        String reason,
        String decisionReason,
        RefundStatus refundStatus,
        LocalDateTime createdAt) {
}
