package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param id             primary key of the underlying
 *                       {@link com.example.aichinese.entity.RefundRequest}
 * @param orderId        primary key of the refunded
 *                       {@link com.example.aichinese.entity.Order};
 *                       {@code null} only on a schema-FK violation
 * @param orderNumber    human-readable order reference
 *                       (e.g. {@code "ORD-XXXXXXXX-XXXXXXXX"});
 *                       {@code null} when {@link #orderId} is also
 *                       {@code null}
 * @param studentId      primary key of the buyer's
 *                       {@link com.example.aichinese.entity.StudentProfile}
 * @param studentName    buyer's display name from
 *                       {@code users.display_name}; nullable when
 *                       the underlying user has been pseudonymised
 * @param studentEmail   buyer's email; nullable for the same reason
 *                       as {@link #studentName}
 * @param amount         refund amount in the order's currency,
 *                       always equals the original order total
 *                       (full-order refund policy), normalised to
 *                       {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                       decimal places
 * @param reason         student-supplied rationale (max
 *                       {@value CommerceDtoConstants#REFUND_REASON_MAX_LENGTH}
 *                       chars) from {@link RefundRequestCreateRequest}
 * @param decisionReason mandatory rejection note supplied by the
 *                       admin via {@link RefundDecisionRequest};
 *                       populated only for
 *                       {@link RefundStatus#REJECTED}, otherwise
 *                       {@code null}
 * @param refundStatus   current state in the {@link RefundStatus}
 *                       state machine
 * @param createdAt      timestamp the student filed the request;
 *                       always populated
 */
@JsonPropertyOrder({
        "id",
        "orderId",
        "orderNumber",
        "studentId",
        "studentName",
        "studentEmail",
        "amount",
        "reason",
        "decisionReason",
        "refundStatus",
        "createdAt"
})
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
