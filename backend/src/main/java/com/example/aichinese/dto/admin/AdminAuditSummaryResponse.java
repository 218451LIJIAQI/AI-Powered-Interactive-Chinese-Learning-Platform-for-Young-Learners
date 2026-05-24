package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Trimmed audit-log row embedded inside larger admin responses
 * (notably {@link AdminTeacherDetailResponse#recentAuditLogs()}).
 *
 * <p>This is deliberately a compact projection of
 * {@link com.example.aichinese.entity.AuditLog} — only the columns the
 * admin "recent activity" widget renders. The omitted columns
 * ({@code actorEmail}, {@code ipAddress}, {@code requestId}) are still
 * available through the full audit-log endpoint when an admin needs to
 * drill in for forensics; keeping them out of this summary keeps the
 * teacher-detail payload small and avoids surfacing IPs in widgets
 * where they are not actionable.</p>
 *
 * <p>{@link #description} is already redacted by
 * {@code AuditLogService.sanitize} before persistence, so safely
 * embedding it here cannot leak Authorization headers or reset
 * tokens that may have appeared in the original request body.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #auditLogId}, {@link #actionType} and {@link #createdAt}
 *       are non-{@code null} — they are the columns the audit widget
 *       renders unconditionally and the deep-link uses.</li>
 *   <li>{@link #targetType} and {@link #description} may be
 *       {@code null} for target-less actions or when the producer
 *       opted not to attach a human description.</li>
 * </ul>
 *
 * @param auditLogId the underlying audit log row id (for "view full
 *                   audit detail" deep-links from the admin UI);
 *                   never {@code null}
 * @param actionType the verb logged by the producer (e.g.
 *                   {@code TEACHER_APPROVAL}); never {@code null}
 * @param targetType the entity type the action targeted (e.g.
 *                   {@code TeacherProfile}); may be {@code null} for
 *                   target-less actions like {@code LOGIN}
 * @param description redacted human-readable description; may be
 *                    {@code null} when the producer logged none
 * @param createdAt   when the audit row was written (Asia/Singapore
 *                    wall clock; see
 *                    {@link com.example.aichinese.entity.base.BaseEntity}
 *                    Javadoc); never {@code null}
 */
@JsonPropertyOrder({ "auditLogId", "actionType", "targetType", "description", "createdAt" })
public record AdminAuditSummaryResponse(
        Long auditLogId,
        String actionType,
        String targetType,
        String description,
        LocalDateTime createdAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the three
     * fields the admin "recent activity" widget renders unconditionally.
     * {@link #targetType} and {@link #description} are intentionally
     * permitted to be {@code null} per the invariants documented above.
     */
    public AdminAuditSummaryResponse {
        Objects.requireNonNull(auditLogId, "auditLogId must not be null");
        Objects.requireNonNull(actionType, "actionType must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
