package com.example.aichinese.dto.communication;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrNull;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Notifications to mark unread again: the ids "Mark all as read" returned (its Undo). */
public record NotificationIdsRequest(
        @NotNull(message = "Choose the notifications to mark as unread.")
        @Size(max = 1000, message = "Too many notifications to mark as unread at once.")
        List<@NotNull(message = "Notification id must not be null.")
                @Positive(message = "Notification id must be positive.") Long> ids) {

    public NotificationIdsRequest {
        ids = immutableListOrNull(ids);
    }
}
