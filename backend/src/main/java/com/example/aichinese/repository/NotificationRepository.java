package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;

import jakarta.persistence.LockModeType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** The user's notifications of one status (any when {@code status} is null), in the page's order. */
    @Query("""
            select notification
            from Notification notification
            where notification.user.id = :userId
              and (:status is null or notification.notificationStatus = :status)
            """)
    Page<Notification> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") NotificationStatus status,
            Pageable pageable);

    long countByUserIdAndNotificationStatus(Long userId, NotificationStatus notificationStatus);

    boolean existsByUserIdAndNotificationType(Long userId, NotificationType notificationType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findLockedByIdAndUserId(Long id, Long userId);

    /** Only these locked rows belong to this mark-all operation and its Undo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n.id from Notification n
            where n.user.id = :userId and n.notificationStatus = UNREAD
            order by n.id asc
            """)
    List<Long> lockUnreadIds(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n.id from Notification n
            where n.user.id = :userId and n.notificationStatus = READ and n.id in :ids
            order by n.id asc
            """)
    List<Long> lockReadIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n set n.notificationStatus = READ, n.readAt = :now, n.updatedAt = :now
            where n.user.id = :userId and n.id in :ids
            """)
    int markLockedRead(@Param("userId") Long userId, @Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n set n.notificationStatus = UNREAD, n.readAt = null, n.updatedAt = :now
            where n.user.id = :userId and n.id in :ids
            """)
    int markLockedUnread(@Param("userId") Long userId, @Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /** Everyone's notifications about these records (they name the account that made them). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.targetType = :targetType and n.targetId in :targetIds")
    int deleteByTarget(@Param("targetType") String targetType, @Param("targetIds") Collection<Long> targetIds);
}
