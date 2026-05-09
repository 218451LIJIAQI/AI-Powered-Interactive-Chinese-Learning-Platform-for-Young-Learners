package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

public record SupportTicketResponse(
        Long id,
        Long requesterUserId,
        String requesterName,
        String requesterEmail,
        String requesterRole,
        String subject,
        String description,
        String status,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
