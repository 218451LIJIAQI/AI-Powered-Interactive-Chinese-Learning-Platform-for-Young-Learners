package com.example.aichinese.dto.dashboard;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Recent notification shown on the student and teacher dashboards. */
@JsonPropertyOrder({
        "notificationId",
        "title",
        "message",
        "status",
        "targetPath",
        "createdAt"
})
public record DashboardNotificationSummary(
        Long notificationId,
        String title,
        String message,
        String status,
        String targetPath,
        LocalDateTime createdAt) {

    public DashboardNotificationSummary {
        Objects.requireNonNull(notificationId, "notificationId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
