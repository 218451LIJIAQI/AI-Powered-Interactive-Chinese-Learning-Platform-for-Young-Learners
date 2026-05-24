package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.PayoutStatus;

/**
 * Admin-facing payout-request row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/admin/payout-requests} (paginated
 *       cross-teacher list)</li>
 *   <li>{@code POST /api/v1/admin/payout-requests/{id}/approve}
 *       (no body)</li>
 *   <li>{@code POST /api/v1/admin/payout-requests/{id}/reject} —
 *       see {@link PayoutDecisionRequest}</li>
 *   <li>{@code POST /api/v1/admin/payout-requests/{id}/mark-paid}
 *       (no body, terminal)</li>
 * </ul>
 *
 * <p>Drives the admin commerce dashboard's payout-triage screen.
 * Mirrors {@link TeacherPayoutRequestResponse} field-for-field
 * plus the requesting teacher's identity ({@link #teacherId},
 * {@link #teacherName}, {@link #teacherEmail}) for direct
 * communication during processing.</p>
 *
 * <p>See {@link TeacherPayoutRequestResponse} for the
 * {@link PayoutStatus} state machine and the impact on
 * {@link TeacherEarningSummaryResponse#availableBalance()}.</p>
 */
public record AdminPayoutRequestResponse(
        Long id,
        Long teacherId,
        String teacherName,
        String teacherEmail,
        BigDecimal amount,
        String requestedNote,
        String decisionReason,
        PayoutStatus payoutStatus,
        LocalDateTime createdAt) {
}
