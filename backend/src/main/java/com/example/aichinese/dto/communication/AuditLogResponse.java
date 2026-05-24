package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

/**
 * Full audit-log row returned by the admin-only audit endpoints
 * (paginated list under {@code /api/v1/admin/audit-logs}).
 *
 * <p>Carries the complete row from
 * {@link com.example.aichinese.entity.AuditLog} including
 * sensitive correlation fields ({@link #ipAddress},
 * {@link #requestId}, {@link #actorEmail}) — only admins should
 * ever see this DTO. The trimmed
 * {@code AdminAuditSummaryResponse} is the lighter projection
 * embedded inside teacher-detail screens that intentionally
 * <em>omits</em> these PII fields.</p>
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
 * <p>{@link #description} is a sanitised free-text blurb (passed
 * through {@code AuditLogService}'s redactor that strips
 * Authorization / Bearer tokens / reset links / token-like
 * assignments). {@link #requestId} correlates back to
 * {@link com.example.aichinese.dto.ApiResponse#requestId()} on the
 * triggering API call so admins can stitch logs across
 * requests.</p>
 *
 * <p>{@link #actorUserId} / {@link #actorEmail} can both be
 * {@code null} for system-generated entries (e.g. scheduled jobs,
 * webhook-driven mutations).</p>
 */
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
}
