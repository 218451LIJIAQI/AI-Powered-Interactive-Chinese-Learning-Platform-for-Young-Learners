package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Full audit-log row returned by the admin-only audit endpoints
 * (paginated list under {@code GET /api/v1/admin/audit-logs} and the
 * single-row lookup {@code GET /api/v1/admin/audit-logs/{id}}).
 *
 * <p>Carries the complete row from
 * {@link com.example.aichinese.entity.AuditLog} including the
 * sensitive correlation fields ({@link #ipAddress},
 * {@link #requestId}, {@link #actorEmail}) — only admins should
 * ever see this DTO. The trimmed
 * {@link com.example.aichinese.dto.admin.AdminAuditSummaryResponse}
 * is the lighter projection embedded inside teacher-detail screens
 * that intentionally <em>omits</em> these PII fields.</p>
 *
 * <h2>{@link #actionType} / {@link #targetType} / {@link #targetId}</h2>
 * <p>Together they identify what was done to what. Examples:</p>
 * <ul>
 *   <li>{@code "TEACHER_VERIFICATION_DOCUMENT_UPLOAD"} on
 *       {@code "TeacherVerificationDocument:42"}</li>
 *   <li>{@code "ADMIN_CONTENT_ARCHIVE"} on
 *       {@code "LearningModule:13"}</li>
 *   <li>{@code "STUDENT_LIVE_CLASS_ENROL"} on
 *       {@code "LiveClass:7"}</li>
 * </ul>
 *
 * <h2>Sensitive-data handling</h2>
 * <p>{@link #description} is a sanitised free-text blurb (passed
 * through {@code AuditLogService}'s redactor that strips
 * {@code Authorization} / {@code Bearer} tokens, reset links, and
 * token-like assignments before persistence). It is also capped at
 * 1&nbsp;000 characters server-side so downstream UIs can rely on
 * bounded sizing.</p>
 *
 * <p>{@link #requestId} correlates back to
 * {@link com.example.aichinese.dto.ApiResponse#requestId()} on the
 * triggering API call so admins can stitch logs across
 * requests.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #actionType} and {@link #createdAt} are
 *       always non-{@code null} — they map to the primary key, a
 *       {@code NOT NULL} column, and the
 *       {@link com.example.aichinese.entity.base.BaseEntity}
 *       managed timestamp respectively.</li>
 *   <li>{@link #actorUserId} / {@link #actorEmail} may both be
 *       {@code null} for system-generated entries (scheduled jobs,
 *       webhook-driven mutations, anonymous failed-login attempts).</li>
 *   <li>{@link #targetType} / {@link #targetId} may be {@code null}
 *       for target-less actions such as {@code LOGIN}.</li>
 *   <li>{@link #description} / {@link #ipAddress} / {@link #requestId}
 *       may be {@code null} when the producer omitted them
 *       (e.g. no current request context for background jobs).</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param id           primary key of the underlying
 *                     {@link com.example.aichinese.entity.AuditLog};
 *                     never {@code null}
 * @param actorUserId  identifier of the {@link com.example.aichinese.entity.User}
 *                     that triggered the action; {@code null} for
 *                     system-generated rows
 * @param actorEmail   denormalised email of the acting user at the
 *                     time the row was written, preserved even after
 *                     the user is deleted or renamed; {@code null}
 *                     for anonymous / system rows
 * @param actionType   verb logged by the producer (e.g.
 *                     {@code TEACHER_APPROVAL}, {@code LOGIN_FAILED});
 *                     never {@code null}
 * @param targetType   entity type the action targeted (e.g.
 *                     {@code TeacherProfile}, {@code LearningModule});
 *                     {@code null} for target-less actions
 * @param targetId     primary key of the affected resource;
 *                     {@code null} when {@link #targetType} is
 *                     {@code null} or the action targets a non-PK row
 * @param description  redacted, length-capped human-readable
 *                     description; {@code null} when the producer
 *                     logged none
 * @param ipAddress    remote address captured from the triggering
 *                     HTTP request; {@code null} for background jobs
 *                     that have no request context
 * @param requestId    per-request correlation id sourced from
 *                     {@code RequestIdHolder}; {@code null} for
 *                     background jobs
 * @param createdAt    timestamp the audit row was written (Asia/Singapore
 *                     wall clock; see
 *                     {@link com.example.aichinese.entity.base.BaseEntity}
 *                     Javadoc); never {@code null}
 */
@JsonPropertyOrder({
        "id",
        "actorUserId",
        "actorEmail",
        "actionType",
        "targetType",
        "targetId",
        "description",
        "ipAddress",
        "requestId",
        "createdAt"
})
public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String actorEmail,
        String actionType,
        String targetType,
        Long targetId,
        String description,
        String ipAddress,
        String requestId,
        LocalDateTime createdAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the three
     * fields that the admin audit list / detail UIs render
     * unconditionally and that the underlying persistence layer
     * guarantees ({@link #id}, {@link #actionType},
     * {@link #createdAt}). All remaining fields are intentionally
     * permitted to be {@code null} per the invariants documented
     * above.
     */
    public AuditLogResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(actionType, "actionType must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
