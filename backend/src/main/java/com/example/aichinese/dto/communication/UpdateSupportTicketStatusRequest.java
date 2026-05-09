package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;

public record UpdateSupportTicketStatusRequest(
        @NotBlank(message = "Status is required.")
        String status) {
}
