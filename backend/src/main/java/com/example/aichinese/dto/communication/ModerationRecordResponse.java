package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

/**
 * Admin-facing moderation history row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/admin/moderation-actions} (paginated
 *       cross-target list)</li>
 *   <li>{@code POST /api/v1/admin/moderation-actions} (echoes the
 *       freshly-created row from
 *       {@link CreateModerationActionRequest})</li>
 * </ul>
 *
 * <p>Drives the admin's "Recent moderation actions" screen,
 * surfacing every row from
 * {@link com.example.aichinese.entity.ContentModerationRecord} —
 * which is written by both the catch-all moderation note flow
 * (see {@link CreateModerationActionRequest}) and the structured
 * archive / approve / reject flows (see
 * {@code ContentApprovalRequest}).</p>
 *
 * <h2>{@link #targetType} / {@link #targetId} / {@link #targetTitle}</h2>
 * <p>Match the create request's vocabulary with one addition:
 * {@link #targetTitle} is denormalised from the underlying row at
 * lookup time so the admin sees "Archived: HSK 1 Foundation Pack"
 * rather than just "PACKAGE:11" in the action log.</p>
 *
 * <h2>{@link #actionTaken}</h2>
 * <p>Short label identifying the action ({@code "ARCHIVE"},
 * {@code "APPROVE"}, {@code "REJECT"}, {@code "NOTE"}, etc.) so
 * the admin UI can render category-appropriate icons.</p>
 *
 * <h2>{@link #relatedTeacherId} / {@link #relatedTeacherName}</h2>
 * <p>Populated when the moderation target is teacher-owned (a
 * lesson / module / package / earning row) so the admin can pivot
 * directly to the affected teacher's profile. {@code null} for
 * non-teacher targets (e.g. moderation notes on student accounts).</p>
 *
 * <p>{@link #adminUserId} / {@link #adminName} attribute the
 * action; never {@code null} (every moderation row must have an
 * admin author).</p>
 */
public record ModerationRecordResponse(
        Long id,
        String targetType,
        Long targetId,
        String targetTitle,
        String actionTaken,
        String note,
        Long adminUserId,
        String adminName,
        Long relatedTeacherId,
        String relatedTeacherName,
        LocalDateTime createdAt) {
}
