package com.example.aichinese.dto.ai;

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
}
