package com.example.aichinese.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.NotificationIdsRequest;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.NotificationUnreadCountResponse;
import com.example.aichinese.entity.enums.EnumLookups;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.NotificationService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/** The signed-in user's notifications: the list, the unread count, Mark as read, Mark all as read and its Undo. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        NotificationStatus statusFilter = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.strip())
                ? null
                : EnumLookups.tryFrom(NotificationStatus.class, status)
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

    /** Marks every unread notification read; answers with their ids, for the Undo ({@code PATCH /unread}). */
    @PatchMapping("/read-all")
    public ApiResponse<List<Long>> markAllAsRead(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Notifications marked as read successfully.", notificationService.markAllAsRead(currentUser));
    }

    /** Marks the caller's given notifications unread again (the Undo of "Mark all as read"); answers with their ids. */
    @PatchMapping("/unread")
    public ApiResponse<List<Long>> markUnread(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody NotificationIdsRequest request) {
        return ApiResponse.success("Notifications marked as unread.", notificationService.markUnread(currentUser, request.ids()));
    }
}
