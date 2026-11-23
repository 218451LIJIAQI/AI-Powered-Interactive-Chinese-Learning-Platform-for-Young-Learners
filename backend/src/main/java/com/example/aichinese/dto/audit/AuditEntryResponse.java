package com.example.aichinese.dto.audit;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;

/** One audit log entry; {@code targetLabel} names what the entry is about in words. */
public record AuditEntryResponse(
        Long id,
        String actionType,
        AuditCategory category,
        String actorName,
        String actorEmail,
        String targetLabel,
        /** The account the entry is about, for "Open user". */
        Long targetUserId,
        String reason,
        String description,
        LocalDateTime createdAt) {

    @Override
    public String toString() {
        return "AuditEntryResponse[id=" + id
                + ", actionType=" + actionType
                + ", category=" + category
                + ", actorName=" + redactValue(actorName)
                + ", actorEmail=" + redactValue(actorEmail)
                + ", targetLabel=" + redactValue(targetLabel)
                + ", targetUserId=" + targetUserId
                + ", reason=" + redactLength(reason)
                + ", description=" + redactLength(description)
                + ", createdAt=" + createdAt + "]";
    }
}
