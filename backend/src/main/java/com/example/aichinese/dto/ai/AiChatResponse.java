package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import com.example.aichinese.entity.enums.AiRequestStatus;

/**
 * One Ask SparkBot turn. On success {@code chatMessageId} is the stored answer (a report names it); otherwise
 * {@code aiResponse} is the fixed message that explains why there is no answer.
 */
public record AiChatResponse(
        Long sessionId,
        Long chatMessageId,
        String aiResponse,
        AiRequestStatus status) {

    @Override
    public String toString() {
        return "AiChatResponse[sessionId=" + sessionId
                + ", chatMessageId=" + chatMessageId
                + ", aiResponse=" + redactLength(aiResponse)
                + ", status=" + status + "]";
    }
}
