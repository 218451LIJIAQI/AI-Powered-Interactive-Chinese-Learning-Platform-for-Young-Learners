package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotBlank(message = "Subject is required.")
        @Size(max = 200, message = "Subject must be 200 characters or fewer.")
        String subject,

        @NotBlank(message = "Message is required.")
        @Size(max = 4000, message = "Message must be 4000 characters or fewer.")
        String message) {
}
