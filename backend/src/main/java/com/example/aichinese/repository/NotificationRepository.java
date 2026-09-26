package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.enums.NotificationStatus;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = { "user", "createdByUser" })
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "user", "createdByUser" })
    @Query("""
            select notification
            from Notification notification
            where notification.user.id = :userId
              and (:beforeCreatedAt is null
                   or notification.createdAt < :beforeCreatedAt
                   or (notification.createdAt = :beforeCreatedAt and notification.id < :beforeId))
            order by notification.createdAt desc, notification.id desc
            """)
    List<Notification> findUserBatchBefore(
            @Param("userId") Long userId,
            @Param("beforeCreatedAt") LocalDateTime beforeCreatedAt,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = { "user", "createdByUser" })
    @Query("""
            select notification
            from Notification notification
            where notification.user.id = :userId
              and notification.notificationStatus = :status
              and (:beforeCreatedAt is null
                   or notification.createdAt < :beforeCreatedAt
                   or (notification.createdAt = :beforeCreatedAt and notification.id < :beforeId))
            order by notification.createdAt desc, notification.id desc
            """)
    List<Notification> findUserStatusBatchBefore(
            @Param("userId") Long userId,
            @Param("status") NotificationStatus status,
            @Param("beforeCreatedAt") LocalDateTime beforeCreatedAt,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = { "user", "createdByUser" })
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
