package com.example.aichinese.dto.support;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.example.aichinese.dto.DtoNormalization;

/** A support request with its whole thread, oldest message first. */
public record SupportRequestDetailResponse(
        SupportRequestSummaryResponse request,
        /** The requester's account (admins open it from the queue); null for a visitor or a deleted account. */
        Long requesterUserId,
        List<Message> messages) {

    public SupportRequestDetailResponse {
        Objects.requireNonNull(request, "request must not be null");
        messages = DtoNormalization.immutableListOrEmpty(messages);
    }

    public record Message(
            Long id,
            boolean fromSupport,
            /** The writer's name; null for a visitor or a deleted account. */
            String authorName,
            String body,
            LocalDateTime createdAt,
            Attachment attachment) {
    }

    /** A screenshot; download it with the message id. */
    public record Attachment(String fileName, long fileSize) {
    }
}
