package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

/**
 * One notification row returned by the per-user notification
 * inbox endpoints (paginated):
 * <ul>
 *   <li>{@code GET /api/v1/notifications} — the calling user's
 *       inbox (any role).</li>
 *   <li>{@code POST /api/v1/notifications/{id}/mark-read} — echoes
 *       the freshly-marked row.</li>
 * </ul>
 *
 * <p>Persists as
 * {@link com.example.aichinese.entity.Notification}; surfaced into
 * dashboards via the trimmed {@code NotificationSummary} embedded
 * inside {@code StudentDashboardResponse} /
 * {@code TeacherDashboardResponse}.</p>
 *
 * <h2>{@link #type}</h2>
 * <p>String form of
 * {@link com.example.aichinese.entity.enums.NotificationType}
 * ({@code "LIVE_CLASS_REMINDER"}, {@code "TEACHER_FEEDBACK_NEW"},
 * {@code "PAYOUT_APPROVED"}, etc.). Drives the front-end icon and
 * routing rules.</p>
 *
 * <h2>{@link #status}</h2>
 * <p>Either {@code "UNREAD"} or {@code "READ"} (string form of
 * {@link com.example.aichinese.entity.enums.NotificationStatus}).
 * Flipping to {@code READ} also stamps {@link #readAt}; the
 * {@link NotificationUnreadCountResponse} badge reflects the
 * count of {@code UNREAD} rows.</p>
 *
 * <h2>{@link #targetType} / {@link #targetId} / {@link #targetPath}</h2>
 * <p>Together drive the click-through navigation:
 * {@link #targetType} ({@code "LESSON"}, {@code "LIVE_CLASS"},
 * {@code "PAYOUT_REQUEST"}, etc.) and {@link #targetId} identify
 * the underlying row; {@link #targetPath} is a pre-formatted
 * front-end route the inbox uses directly (e.g.
 * {@code "/student/classes"}). All three may be {@code null} for
 * informational-only notifications with no destination.</p>
 *
 * <p>{@link #readAt} is set only when {@link #status} is
 * {@code READ}; {@link #createdAt} is always populated.</p>
 */
public record NotificationResponse(
        Long id,
        String title,
        String message,
        String type,
        String status,
        String targetType,
        Long targetId,
        String targetPath,
        LocalDateTime createdAt,
        LocalDateTime readAt) {
}
