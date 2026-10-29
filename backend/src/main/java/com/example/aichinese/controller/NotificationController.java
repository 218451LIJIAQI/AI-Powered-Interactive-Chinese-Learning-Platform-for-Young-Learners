package com.example.aichinese.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.NotificationUnreadCountResponse;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        NotificationStatus statusFilter = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.strip())
                ? null
                : NotificationStatus.tryFrom(status)
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Status must be UNREAD, READ or ALL."));
        return ApiResponse.success(
                "Notifications retrieved successfully.",
                notificationService.getCurrentUserNotifications(currentUser, statusFilter, page, size));
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
    public ApiResponse<Void> markAllAsRead(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        notificationService.markAllAsRead(currentUser);
        return ApiResponse.success("Notifications marked as read successfully.", null);
    }
}
