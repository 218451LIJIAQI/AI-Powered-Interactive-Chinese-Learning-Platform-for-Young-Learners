package com.example.aichinese.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.communication.BulkOperationResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.NotificationUnreadCountResponse;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.security.SecurityUserDetails;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final CommunicationMapper communicationMapper;

    public NotificationService(
            NotificationRepository notificationRepository,
            CommunicationMapper communicationMapper) {
        this.notificationRepository = notificationRepository;
        this.communicationMapper = communicationMapper;
    }

    @Transactional
    public void createNotification(User recipient, String title, String message, String targetPath) {
        createNotification(recipient, NotificationType.SYSTEM, title, message, null, null, targetPath, null);
    }

    @Transactional
    public Notification createNotification(
            User recipient,
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath,
            User createdByUser) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationType(notificationType);
        notification.setNotificationStatus(NotificationStatus.UNREAD);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setTargetPath(targetPath);
        notification.setCreatedByUser(createdByUser);
        notification.setReadAt(null);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void safeCreateNotification(
            User recipient,
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath,
            User createdByUser) {
        try {
            createNotification(recipient, notificationType, title, message, targetType, targetId, targetPath, createdByUser);
        } catch (Exception exception) {
            log.warn("Notification creation failed for recipient {}.", recipient != null ? recipient.getEmail() : "unknown", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getCurrentUserNotifications(SecurityUserDetails currentUser) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(communicationMapper::toNotificationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount(SecurityUserDetails currentUser) {
        return new NotificationUnreadCountResponse(
                notificationRepository.countByUserIdAndNotificationStatus(currentUser.getUserId(), NotificationStatus.UNREAD));
    }

    @Transactional
    public NotificationResponse markAsRead(SecurityUserDetails currentUser, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Notification was not found."));

        if (notification.getNotificationStatus() == NotificationStatus.UNREAD) {
            notification.setNotificationStatus(NotificationStatus.READ);
            notification.setReadAt(LocalDateTime.now());
        }

        return communicationMapper.toNotificationResponse(notification);
    }

    @Transactional
    public BulkOperationResponse markAllAsRead(SecurityUserDetails currentUser) {
        long updatedCount = 0;
        for (Notification notification : notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId())) {
            if (notification.getNotificationStatus() == NotificationStatus.UNREAD) {
                notification.setNotificationStatus(NotificationStatus.READ);
                notification.setReadAt(LocalDateTime.now());
                updatedCount++;
            }
        }
        return new BulkOperationResponse(updatedCount);
    }
}
