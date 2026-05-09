package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.communication.BulkOperationResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.NotificationUnreadCountResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.NotificationService;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Notifications retrieved successfully.",
                notificationService.getCurrentUserNotifications(currentUser));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Notification unread count retrieved successfully.",
                notificationService.getUnreadCount(currentUser));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markAsRead(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Notification marked as read successfully.",
                notificationService.markAsRead(currentUser, id));
    }

    @PatchMapping("/read-all")
    public ApiResponse<BulkOperationResponse> markAllAsRead(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Notifications marked as read successfully.",
                notificationService.markAllAsRead(currentUser));
    }
}
