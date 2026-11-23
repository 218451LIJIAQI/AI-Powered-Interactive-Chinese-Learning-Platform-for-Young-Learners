package com.example.aichinese.dto.support;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportRequesterRole;
import com.example.aichinese.entity.enums.SupportTopic;

/** One support request in the admin queue or on the admin dashboard. */
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
        /** While answered: when the request closes by itself without a reply. */
        LocalDateTime closesAt,
        LocalDateTime solvedAt,
        SupportClosedBy closedBy) {

    @Override
    public String toString() {
        return "SupportRequestSummaryResponse[id=" + id
                + ", number=" + number
                + ", topic=" + topic
                + ", subject=" + redactLength(subject)
                + ", status=" + status
                + ", requesterName=" + redactValue(requesterName)
                + ", requesterEmail=" + redactValue(requesterEmail)
                + ", requesterRole=" + requesterRole
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + ", closesAt=" + closesAt
                + ", solvedAt=" + solvedAt
                + ", closedBy=" + closedBy + "]";
    }
}
