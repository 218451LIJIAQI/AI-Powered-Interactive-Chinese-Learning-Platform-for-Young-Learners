package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.enums.NotificationStatus;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = { "user", "createdByUser" })
    List<Notification> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "user", "createdByUser" })
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "user", "createdByUser" })
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndNotificationStatus(Long userId, NotificationStatus notificationStatus);

    /**
     * Marks every UNREAD notification owned by the supplied user as READ in a
     * single SQL UPDATE. Replaces the previous load-then-iterate path which
     * issued one UPDATE per row through the persistence context — that is
     * O(n) round-trips and becomes painful for users with many notifications.
     *
     * <p>The bulk update bypasses the persistence context, so callers that
     * have already loaded {@link Notification} entities into the same
     * transaction must be careful not to rely on the in-memory copies after
     * the update — they will be stale.</p>
     *
     * @return the number of rows that were transitioned to READ.
     */
    @Modifying
    @Query("update Notification n set n.notificationStatus = :readStatus, n.readAt = :readAt "
            + "where n.user.id = :userId and n.notificationStatus = :unreadStatus")
    int markAllAsReadForUser(
            @Param("userId") Long userId,
            @Param("unreadStatus") NotificationStatus unreadStatus,
            @Param("readStatus") NotificationStatus readStatus,
            @Param("readAt") LocalDateTime readAt);
}
