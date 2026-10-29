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
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_status", columnList = "notification_status"),
        @Index(name = "idx_notifications_type", columnList = "notification_type"),
        @Index(name = "idx_notifications_user_created_id", columnList = "user_id,created_at,id"),
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
