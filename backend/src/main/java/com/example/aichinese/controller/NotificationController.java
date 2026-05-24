package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.BulkOperationResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.NotificationUnreadCountResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.NotificationService;

/**
 * In-app notification inbox endpoints shared by every authenticated
 * persona (student / teacher / admin).
 *
 * <h2>Operations</h2>
 * <ul>
 *   <li>{@code GET /} — paginated inbox; {@code page}/{@code size}
 *       clamped by {@link com.example.aichinese.util.PaginationUtils}.</li>
 *   <li>{@code GET /unread-count} — lightweight badge count for the
 *       top-bar bell icon.</li>
 *   <li>{@code PATCH /{id}/read} — mark a single notification read.</li>
 *   <li>{@code PATCH /read-all} — bulk mark all unread as read; the
 *       service runs in its own transaction so the badge updates
 *       promptly without blocking the surrounding page-load.</li>
 * </ul>
 *
 * <p>Row-level access is enforced at the service layer: a caller can
 * only see, and mark read, their own notifications. The controller
 * itself just relays the
 * {@link com.example.aichinese.security.SecurityUserDetails} principal.</p>
 */
@Validated
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
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Notifications retrieved successfully.",
                notificationService.getCurrentUserNotifications(currentUser, page, size));
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
