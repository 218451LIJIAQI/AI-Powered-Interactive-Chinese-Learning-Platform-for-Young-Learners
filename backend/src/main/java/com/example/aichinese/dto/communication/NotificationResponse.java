package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One notification row returned by the per-user notification inbox
 * endpoints exposed under {@code /api/v1/notifications}:
 * <ul>
 *   <li>{@code GET /api/v1/notifications} — the calling user's
 *       inbox, paginated newest-first (any authenticated role).</li>
 *   <li>{@code PATCH /api/v1/notifications/{id}/read} — flips the
 *       row to {@code READ} and echoes the freshly-marked row.</li>
 * </ul>
 *
 * <p>Persists as {@link com.example.aichinese.entity.Notification};
 * mapped via {@link com.example.aichinese.service.CommunicationMapper}.
 * The companion endpoints {@code GET /unread-count} and
 * {@code PATCH /read-all} return
 * {@link NotificationUnreadCountResponse} and
 * {@link BulkOperationResponse} respectively rather than this DTO.</p>
 *
 * <h2>{@link #type}</h2>
 * <p>{@code .name()} of
 * {@link com.example.aichinese.entity.enums.NotificationType}; one
 * of {@code SYSTEM}, {@code TEACHER_FEEDBACK}, {@code QUIZ_RESULT},
 * {@code PROGRESS_UPDATE}, {@code REWARD_EARNED},
 * {@code PAYMENT_STATUS}, {@code REFUND_STATUS},
 * {@code PAYOUT_STATUS}, {@code LIVE_CLASS_REMINDER},
 * {@code TEACHER_APPROVAL}, {@code CONTENT_REVIEW},
 * {@code MODERATION_ACTION}, {@code AI_REPORT_REVIEW}, or
 * {@code ADMIN_TASK}. Drives the inbox icon and the front-end
 * routing/grouping rules. Stored as a {@code String} (not the enum)
 * so notifications written before a future enum extension still
 * deserialise on the client.</p>
 *
 * <h2>{@link #status}</h2>
 * <p>Either {@code "UNREAD"} or {@code "READ"} ({@code .name()} of
 * {@link com.example.aichinese.entity.enums.NotificationStatus}).
 * Flipping to {@code READ} also stamps {@link #readAt}; the
 * {@link NotificationUnreadCountResponse} badge reflects the count
 * of {@code UNREAD} rows for the calling user.</p>
 *
 * <h2>{@link #targetType} / {@link #targetId} / {@link #targetPath}</h2>
 * <p>Together they drive click-through navigation when the
 * notification has a destination. {@code (targetType, targetId)}
 * polymorphically identifies the underlying row (e.g.
 * {@code ("LIVE_CLASS", 42)}, {@code ("PAYOUT_REQUEST", 17)}) so
 * the front-end can run server-side accessibility checks via
 * {@code NotificationService.isNotificationCurrentlyAccessible}
 * before deep-linking. {@link #targetPath} is a pre-formatted
 * front-end route (e.g. {@code "/student/classes"}) the inbox
 * uses directly without server consultation. All three may be
 * {@code null} for informational-only notifications.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #title}, {@link #message},
 *       {@link #type}, {@link #status} and {@link #createdAt} are
 *       always non-{@code null} — they map to the primary key, the
 *       {@code NOT NULL} columns, and the managed timestamp on
 *       {@link com.example.aichinese.entity.base.BaseEntity}.</li>
 *   <li>{@link #targetType}, {@link #targetId} and
 *       {@link #targetPath} may each be {@code null} independently
 *       (informational notifications carry none of them).</li>
 *   <li>{@link #readAt} is non-{@code null} when and only when
 *       {@link #status} equals {@code "READ"} — the canonical
 *       constructor does not enforce this cross-field invariant
 *       because mark-read is a separate, transactional flow that
 *       owns the consistency guarantee.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param id          primary key of the underlying
 *                    {@link com.example.aichinese.entity.Notification};
 *                    never {@code null}
 * @param title       short headline shown in the inbox row;
 *                    never {@code null}
 * @param message     full notification body, plain text; never
 *                    {@code null}
 * @param type        {@code .name()} of
 *                    {@link com.example.aichinese.entity.enums.NotificationType};
 *                    never {@code null}
 * @param status      {@code .name()} of
 *                    {@link com.example.aichinese.entity.enums.NotificationStatus}
 *                    ({@code "UNREAD"} or {@code "READ"}); never
 *                    {@code null}
 * @param targetType  optional discriminator identifying the target
 *                    row this notification deep-links to (e.g.
 *                    {@code "LIVE_CLASS"})
 * @param targetId    optional primary key of the target row implied
 *                    by {@link #targetType}
 * @param targetPath  optional pre-formatted front-end route to
 *                    deep-link the user to
 * @param createdAt   timestamp the notification was queued for the
 *                    recipient; never {@code null}
 * @param readAt      timestamp the recipient marked the notification
 *                    read; {@code null} when {@link #status} is
 *                    {@code "UNREAD"}
 */
@JsonPropertyOrder({
        "id",
        "title",
        "message",
        "type",
        "status",
        "targetType",
        "targetId",
        "targetPath",
        "createdAt",
        "readAt"
})
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

    /**
     * Defensive canonical constructor: enforces non-null on every
     * field that the persistence layer guarantees ({@link #id},
     * {@link #title}, {@link #message}, {@link #type},
     * {@link #status}, {@link #createdAt}). The optional target
     * triple and {@link #readAt} are intentionally permitted to be
     * {@code null} per the invariants documented above.
     */
    public NotificationResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
