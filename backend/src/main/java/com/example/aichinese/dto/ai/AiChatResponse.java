package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

public record AiChatResponse(
        Long sessionId,
        String userMessage,
        String aiResponse,
        String language,
        LocalDateTime createdAt,
        String status) {
}
