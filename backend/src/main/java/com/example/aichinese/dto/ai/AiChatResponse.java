package com.example.aichinese.dto.ai;

/**
 * One Ask SparkBot turn. On success {@code chatMessageId} is the stored answer (a report names it); otherwise
 * {@code aiResponse} is the fixed message that explains why there is no answer.
 */
public record AiChatResponse(
        Long sessionId,
        Long chatMessageId,
        String aiResponse,
        String status) {
}
