package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum NotificationStatus {

    UNREAD,

    READ;

    private static final Map<String, NotificationStatus> LOOKUP =
            EnumLookups.indexByName(NotificationStatus.class);

    public static Optional<NotificationStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static NotificationStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Notification status is required.",
                () -> "Invalid notification status. Allowed values are UNREAD or READ.");
    }
}
