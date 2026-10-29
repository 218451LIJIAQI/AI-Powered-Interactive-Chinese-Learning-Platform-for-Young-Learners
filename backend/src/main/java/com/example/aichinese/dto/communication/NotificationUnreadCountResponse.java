package com.example.aichinese.dto.communication;

public record NotificationUnreadCountResponse(
        long unreadCount) {

    public NotificationUnreadCountResponse {
        if (unreadCount < 0L) {
            throw new IllegalArgumentException(
                    "unreadCount must be non-negative but was " + unreadCount);
        }
    }
}
