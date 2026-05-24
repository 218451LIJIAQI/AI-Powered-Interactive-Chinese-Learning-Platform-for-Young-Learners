package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

/**
 * Read/unread state of an in-app notification.
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on the
 * {@code Notification} entity. New entries default to {@link #UNREAD}
 * server-side and are flipped to {@link #READ} when the recipient marks
 * them as such. The constant names are part of the database contract —
 * they MUST NOT be renamed or removed without a paired Flyway migration.</p>
 */
public enum NotificationStatus {

    /** The recipient has not yet acknowledged this notification. */
    UNREAD,

    /** The recipient has explicitly acknowledged this notification. */
    READ;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, NotificationStatus> LOOKUP =
            EnumLookups.indexByName(NotificationStatus.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised status
     */
    public static Optional<NotificationStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised status
     */
    public static NotificationStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Notification status is required.",
                () -> "Invalid notification status. Allowed values are UNREAD or READ.");
    }

    /** @return {@code true} when the recipient has acknowledged the notification. */
    public boolean isRead() {
        return this == READ;
    }

    /** @return {@code true} when the recipient has not yet acknowledged the notification. */
    public boolean isUnread() {
        return this == UNREAD;
    }
}
