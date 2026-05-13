package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
