package com.example.aichinese.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void createNotification(User recipient, String title, String message, String targetPath) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationStatus(NotificationStatus.UNREAD);
        notification.setTargetPath(targetPath);
        notificationRepository.save(notification);
    }
}
