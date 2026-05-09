package com.example.aichinese.service;

import org.springframework.stereotype.Service;

import com.example.aichinese.entity.User;

@Service
public class CommerceNotificationService {

    private final NotificationService notificationService;

    public CommerceNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void notifyUser(User user, String title, String message, String targetPath) {
        notificationService.createNotification(user, title, message, targetPath);
    }
}
