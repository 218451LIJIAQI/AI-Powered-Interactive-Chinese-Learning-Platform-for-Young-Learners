package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param id             primary key of the underlying
 *                       {@link com.example.aichinese.entity.PayoutRequest}
 * @param teacherId      primary key of the requesting teacher's
 *                       {@link com.example.aichinese.entity.TeacherProfile};
 *                       {@code null} only when the teacher row was
 *                       hard-deleted, which violates the schema FK
 * @param teacherName    teacher's display name from
 *                       {@code users.display_name}; nullable when
 *                       the underlying user has been pseudonymised
 * @param teacherEmail   teacher's email; nullable for the same
 *                       reason as {@link #teacherName}
 * @param amount         requested withdrawal amount in the platform
 *                       base currency, normalised to
 *                       {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                       decimal places
 * @param requestedNote  optional free-text note supplied by the
 *                       teacher when filing the request (max
 *                       {@value CommerceDtoConstants#PAYOUT_NOTE_MAX_LENGTH}
 *                       chars); shown verbatim to the admin reviewer
 * @param decisionReason mandatory rejection note supplied by the
 *                       admin via {@link PayoutDecisionRequest};
 *                       populated only for
 *                       {@link PayoutStatus#REJECTED}, otherwise
 *                       {@code null}
 * @param payoutStatus   current state in the
 *                       {@link PayoutStatus} state machine
 * @param createdAt      timestamp the teacher filed the request;
 *                       always populated
 */
@JsonPropertyOrder({
        "id",
        "teacherId",
        "teacherName",
        "teacherEmail",
        "amount",
        "currencyCode",
        "requestedNote",
        "decisionReason",
        "payoutStatus",
        "createdAt"
})
public record AdminPayoutRequestResponse(
        Long id,
        Long teacherId,
        String teacherName,
        String teacherEmail,
        BigDecimal amount,
        String currencyCode,
        String requestedNote,
        String decisionReason,
        PayoutStatus payoutStatus,
        LocalDateTime createdAt) {
}
