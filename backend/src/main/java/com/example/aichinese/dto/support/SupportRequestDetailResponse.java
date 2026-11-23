package com.example.aichinese.dto.support;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;
import java.util.List;

/** A support request with its whole thread, oldest message first. */
public record SupportRequestDetailResponse(
        SupportRequestSummaryResponse request,
        /** The requester's account (admins open it from the queue); null for a visitor. */
        Long requesterUserId,
        List<Message> messages) {

    public record Message(
            Long id,
            boolean fromSupport,
            /** The writer's name; null for a visitor. */
            String authorName,
            String body,
            LocalDateTime createdAt,
            Attachment attachment) {

        @Override
        public String toString() {
            return "Message[id=" + id
                    + ", fromSupport=" + fromSupport
                    + ", authorName=" + redactValue(authorName)
                    + ", body=" + redactLength(body)
                    + ", createdAt=" + createdAt
                    + ", attachment=" + attachment + "]";
        }
    }

    /** A screenshot; download it with the message id. */
    public record Attachment(String fileName, long fileSize) {

        /** The original file name can carry personal text (a name, an ID number), like the message itself. */
        @Override
        public String toString() {
            return "Attachment[fileName=" + redactValue(fileName) + ", fileSize=" + fileSize + "]";
        }
    }
}
