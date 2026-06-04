package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "sessionId", "userMessage", "aiResponse", "language", "createdAt", "status" })
public record AiChatResponse(
        Long sessionId,
        String userMessage,
        String aiResponse,
        String language,
        LocalDateTime createdAt,
        String status) {
}
