package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.enums.NotificationStatus;

/**
 * Persistence operations for {@link Notification} \u2014 the in-app
 * notification rows surfaced in the bell icon (e.g. "Your teacher
 * approved your enrolment", "Your live class is in 10 minutes").
 *
 * <p>Every read eager-fetches {@code user} and {@code createdByUser}
 * so the rendered list can show both the recipient (for admin moderation
 * views) and the actor (for teacher/admin-initiated notifications)
 * without N+1 lazy loads. The {@code findByIdAndUserId} co-validating
 * lookup keeps the mark-as-read endpoint IDOR-safe: an empty
 * {@link Optional} is mapped to a 404 by the service layer so the
 * existence of cross-user notification ids is never disclosed.</p>
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Returns every notification for a user ordered newest-first.
     * Backed by the explicit composite index on
     * {@code (user_id, created_at, id)} \u2014 the bell icon's drop-down
     * uses this for the unbounded "all notifications" view.
     */
    @EntityGraph(attributePaths = { "user", "createdByUser" })
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Status-filtered variant for the unread-only view used by the
     * bell-icon counter and the "unread first" tab in the notifications
     * panel.
     */
    @EntityGraph(attributePaths = { "user", "createdByUser" })
    List<Notification> findByUserIdAndNotificationStatusOrderByCreatedAtDesc(
            Long userId,
            NotificationStatus notificationStatus);

    /**
     * IDOR-safe single-row lookup used by the mark-as-read flow: the
     * row is returned only when {@code userId} owns it. The service
     * layer maps an empty {@link Optional} to a 404 so the existence
     * of cross-user notification ids is never disclosed.
     */
    @EntityGraph(attributePaths = { "user", "createdByUser" })
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
