package com.example.aichinese.dto.support;

import java.time.LocalDateTime;
import java.util.Objects;

import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportRequesterRole;
import com.example.aichinese.entity.enums.SupportTopic;

/** One support request in a list ("Your requests" and the admin queue). */
public record SupportRequestSummaryResponse(
        Long id,
        int number,
        SupportTopic topic,
        String subject,
        SupportRequestStatus status,
        /** The requester's name; null for a visitor. */
        String requesterName,
        String requesterEmail,
        SupportRequesterRole requesterRole,
        LocalDateTime createdAt,
        /** The last change (a new message or a status change). */
        LocalDateTime updatedAt,
        LocalDateTime lastSupportReplyAt,
        /** While answered: when the request closes by itself without a reply. */
        LocalDateTime closesAt,
        LocalDateTime solvedAt,
        SupportClosedBy closedBy) {

    public SupportRequestSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
