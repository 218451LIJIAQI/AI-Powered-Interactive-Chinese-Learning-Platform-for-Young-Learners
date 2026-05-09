package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

public record SupportTicketMessageResponse(
        Long id,
        Long senderUserId,
        String senderName,
        String senderRole,
        String message,
        LocalDateTime createdAt) {
}
