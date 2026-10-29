package com.example.aichinese.entity.enums;

import java.util.Optional;

public enum NotificationStatus {

    UNREAD,

    READ;

    public static Optional<NotificationStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(NotificationStatus.class, value);
    }
}
