package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * In-app notification delivered to a single recipient {@link User}.
 *
 * <p>Notifications are produced by every cross-cutting flow in the
 * platform — quiz grading, payment status changes, teacher feedback,
 * AI safety reports, admin escalations — and rendered in the bell
 * dropdown of the recipient's role-specific dashboard.</p>
 *
 * <p>{@link #notificationType} categorises the notification (drives
 * icon, colour and routing); see
 * {@link com.example.aichinese.entity.enums.NotificationType}.
 * {@link #notificationStatus} tracks read/unread; {@link #readAt} is
 * stamped the first time the recipient acknowledges it.</p>
 *
 * <p>{@link #targetType}, {@link #targetId} and {@link #targetPath}
 * point at the artefact the notification refers to — the front-end
 * uses {@code targetPath} to deep-link the click action without needing
 * to know per-type routing rules. Both target columns are denormalised
 * (no FK) so deleting the target does not cascade-purge historic
 * notifications.</p>
 *
 * <p>{@link #createdByUser} attributes the source actor (teacher posting
 * feedback, admin acting on a moderation case); nullable for system
 * notifications.</p>
 *
 * <p>The composite index
 * {@code (user_id, notification_status, created_at, id)} backs the
 * "unread bell" query that paginates by recency.</p>
 *
 * <h3>Field mutability contract</h3>
 *
 * <p>Every field except the read-state pair is mapped
 * {@code updatable = false}: once a notification is dispatched, its
 * recipient, content, type and target metadata cannot be rewritten
 * through the application layer.</p>
 *
 * <ul>
 *   <li><b>Immutable</b>: {@link #user}, {@link #title},
 *       {@link #message}, {@link #notificationType},
 *       {@link #targetType}, {@link #targetId}, {@link #targetPath},
 *       {@link #createdByUser}. The single legitimate write path is
 *       {@code NotificationService#send}.</li>
 *   <li><b>Mutable</b>: {@link #notificationStatus} and
 *       {@link #readAt}, both touched only by
 *       {@code NotificationService#markAsRead} / bulk equivalents.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_status", columnList = "notification_status"),
        @Index(name = "idx_notifications_type", columnList = "notification_type"),
        @Index(name = "idx_notifications_user_status_created_id", columnList = "user_id,notification_status,created_at,id")
})
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "title", nullable = false, updatable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "message", nullable = false, updatable = false, columnDefinition = "LONGTEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, updatable = false, length = 40)
    private NotificationType notificationType = NotificationType.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_status", nullable = false, length = 20)
    private NotificationStatus notificationStatus = NotificationStatus.UNREAD;

    @Column(name = "target_type", updatable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    @Column(name = "target_path", updatable = false, length = 255)
    private String targetPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", updatable = false)
    private User createdByUser;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
