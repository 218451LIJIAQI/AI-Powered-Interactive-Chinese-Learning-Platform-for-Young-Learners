package com.example.aichinese.dto.communication;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        String type,
        String status,
        String targetPath,
        LocalDateTime createdAt) {

    /** Titles and messages name learners and quote visitors' e-mail addresses: keep them out of logs. */
    @Override
    public String toString() {
        return "NotificationResponse[id=" + id
                + ", title=" + redactLength(title)
                + ", message=" + redactLength(message)
                + ", type=" + type
                + ", status=" + status
                + ", targetPath=" + targetPath
                + ", createdAt=" + createdAt + "]";
    }
}
