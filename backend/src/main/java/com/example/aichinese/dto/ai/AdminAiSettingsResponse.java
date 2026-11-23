package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;

/**
 * The admin AI settings: the AI notice shown with every AI answer, the sensitive-word filter, the AI safety check,
 * the request limits (read-only, from the server configuration) and who changed the settings last.
 */
public record AdminAiSettingsResponse(
        String aiNoticeText,
        boolean sensitiveWordFilter,
        boolean aiSafetyCheck,
        int requestsPerMinute,
        int requestsPerDay,
        LocalDateTime lastChangedAt,
        String lastChangedBy) {

    @Override
    public String toString() {
        return "AdminAiSettingsResponse[sensitiveWordFilter=" + sensitiveWordFilter
                + ", aiSafetyCheck=" + aiSafetyCheck
                + ", requestsPerMinute=" + requestsPerMinute
                + ", requestsPerDay=" + requestsPerDay
                + ", lastChangedAt=" + lastChangedAt
                + ", lastChangedBy=" + redactValue(lastChangedBy) + "]";
    }
}
